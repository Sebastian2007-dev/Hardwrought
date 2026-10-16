package de.ipnats.hardwrought.underworld;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.health.Disease;
import de.ipnats.hardwrought.health.Diseases;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Stage 5: the Graveyard's undead. Old, rotting kinds of the zombie and the skeleton, the only things that
 * spawn down here (in the Graveyard; the skeletons also in the Ossuary), and they do not walk in: they come
 * up out of the ground. First the soil shifts — a sound, dust and soil thrown up — then the thing rises out
 * of it over two seconds, and only then does it move.
 *
 * <ul>
 *   <li><b>Grave walker</b>: a zombie a long time in the ground. Slower than a zombie, harder to put down,
 *   and its bite carries grave dirt into the wound. It never burns and never drowns into anything else.</li>
 *   <li><b>Grave skeleton</b>: bones stained dark by the soil, with an old bow or a worn iron sword.</li>
 * </ul>
 */
public final class GraveUndead {
    /** Soil shifting before anything shows, then the rise; in ticks. */
    public static final int SHIFTING = 24, RISING = 40;
    /** How far under the floor they start. */
    static final double BURIED = 2.1;

    public static final EntityType<GraveWalker> GRAVE_WALKER = register("grave_walker",
            EntityType.Builder.of(GraveWalker::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
                    .ridingOffset(-0.7f).clientTrackingRange(8).notInPeaceful());
    public static final EntityType<GraveSkeleton> GRAVE_SKELETON = register("grave_skeleton",
            EntityType.Builder.of(GraveSkeleton::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f)
                    .ridingOffset(-0.7f).clientTrackingRange(8).notInPeaceful());

    public static final Item GRAVE_WALKER_SPAWN_EGG = egg("grave_walker_spawn_egg", GRAVE_WALKER);
    public static final Item GRAVE_SKELETON_SPAWN_EGG = egg("grave_skeleton_spawn_egg", GRAVE_SKELETON);

    private GraveUndead() { }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(GRAVE_WALKER, Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 28.0).add(Attributes.MOVEMENT_SPEED, 0.19).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 3.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.3));
        FabricDefaultAttributeRegistry.register(GRAVE_SKELETON, AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 22.0).add(Attributes.MOVEMENT_SPEED, 0.23));
        SpawnPlacements.register(GRAVE_WALKER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        SpawnPlacements.register(GRAVE_SKELETON, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
    }

    public static List<Item> items() {
        return List.of(GRAVE_WALKER_SPAWN_EGG, GRAVE_SKELETON_SPAWN_EGG);
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static Item egg(String name, EntityType<?> type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
    }

    // ------------------------------------------------------------------------------------ rising

    /** How far along a mob's rise out of the ground is; held by each of the two kinds. */
    static final class Rise {
        int left;
        double floorY;

        /** Buries the mob under where it stands and starts its rise; for natural spawns and tests. */
        void start(Mob mob) {
            floorY = mob.getY();
            left = SHIFTING + RISING;
            mob.setPos(mob.getX(), floorY - BURIED, mob.getZ());
        }

        boolean rising() {
            return left > 0;
        }

        /**
         * One tick of it, on the server: the soil shifting, then the mob coming up through it. Returns true
         * while it lasts, and the mob does nothing else meanwhile.
         */
        boolean tick(Mob mob) {
            if (left <= 0 || !(mob.level() instanceof ServerLevel level)) return false;
            left--;
            BlockPos floor = BlockPos.containing(mob.getX(), floorY - 0.5, mob.getZ());
            BlockState soil = level.getBlockState(floor);
            if (soil.isAir()) soil = net.minecraft.world.level.block.Blocks.SOUL_SOIL.defaultBlockState();
            var dust = new BlockParticleOption(ParticleTypes.BLOCK, soil);
            RandomSource random = level.getRandom();
            int risen = SHIFTING + RISING - left;
            if (risen <= SHIFTING) {
                // The soil shifts: you hear it first.
                if (risen % 6 == 1) {
                    level.playSound(null, floor, soil.getSoundType().getBreakSound(), SoundSource.HOSTILE, 0.9f, 0.5f + random.nextFloat() * 0.2f);
                    level.sendParticles(dust, mob.getX(), floorY + 0.05, mob.getZ(), 6, 0.3, 0.02, 0.3, 0.05);
                }
                if (risen == SHIFTING) level.playSound(null, floor, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.2f, 0.4f);
            } else {
                double y = floorY - BURIED + BURIED * (risen - SHIFTING) / (double) RISING;
                mob.setPos(mob.getX(), y, mob.getZ());
                mob.setDeltaMovement(0, 0, 0);
                level.sendParticles(dust, mob.getX(), floorY + 0.1, mob.getZ(), 4, 0.35, 0.05, 0.35, 0.08);
                if (risen % 10 == 0) {
                    level.playSound(null, floor, soil.getSoundType().getStepSound(), SoundSource.HOSTILE, 1.0f, 0.6f);
                    level.sendParticles(ParticleTypes.SOUL, mob.getX(), floorY + 0.3, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
                }
                if (left == 0) {
                    mob.setPos(mob.getX(), floorY, mob.getZ());
                    mob.playAmbientSound();
                }
            }
            return true;
        }

        void save(ValueOutput output) {
            if (left > 0) {
                output.putInt("rising", left);
                output.putDouble("rising_floor", floorY);
            }
        }

        void load(ValueInput input) {
            left = input.getIntOr("rising", 0);
            floorY = input.getDoubleOr("rising_floor", 0);
        }
    }

    /** Whether a spawn is one that comes up out of the ground: natural ones, in the Underworld. */
    static boolean risesOnSpawn(ServerLevelAccessor level, EntitySpawnReason reason) {
        return (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION)
                && level.getLevel().dimension() == Underworld.LEVEL;
    }

    // ------------------------------------------------------------------------------------ the two kinds

    /** A zombie a long time in the ground: slow, tough, and its bite festers. */
    public static class GraveWalker extends Zombie {
        final Rise rise = new Rise();

        public GraveWalker(EntityType<? extends GraveWalker> type, Level level) {
            super(type, level);
        }

        /** Starts the rise out of the ground from where it stands. */
        public void rise() {
            rise.start(this);
        }

        public boolean rising() {
            return rise.rising();
        }

        @Override
        public void tick() {
            if (rise.tick(this)) return;
            super.tick();
        }

        @Override
        public boolean isBaby() {
            return false;
        }

        @Override
        protected boolean isSunSensitive() {
            return false;
        }

        @Override
        protected boolean convertsInWater() {
            return false;
        }

        @Override
        public float getVoicePitch() {
            return 0.65f + random.nextFloat() * 0.1f;
        }

        @Override
        public boolean doHurtTarget(ServerLevel level, Entity target) {
            boolean hit = super.doHurtTarget(level, target);
            // Grave dirt in the bite.
            if (hit && target instanceof ServerPlayer player && random.nextInt(3) == 0) Diseases.infect(player, Disease.WOUND, 12);
            return hit;
        }

        @Override
        public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                     @Nullable SpawnGroupData data) {
            SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
            setBaby(false);
            if (risesOnSpawn(level, reason)) rise();
            return result;
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            rise.save(output);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            rise.load(input);
        }
    }

    /** A skeleton the soil has stained dark, with an old bow or a worn iron sword. */
    public static class GraveSkeleton extends Skeleton {
        final Rise rise = new Rise();

        public GraveSkeleton(EntityType<? extends GraveSkeleton> type, Level level) {
            super(type, level);
        }

        public void rise() {
            rise.start(this);
        }

        public boolean rising() {
            return rise.rising();
        }

        @Override
        public void tick() {
            if (rise.tick(this)) return;
            super.tick();
        }

        @Override
        public float getVoicePitch() {
            return 0.7f + random.nextFloat() * 0.1f;
        }

        @Override
        protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
            super.populateDefaultEquipmentSlots(random, difficulty);
            if (random.nextInt(5) < 2) {
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.setDamageValue(sword.getMaxDamage() / 2 + random.nextInt(sword.getMaxDamage() / 3));
                setItemSlot(EquipmentSlot.MAINHAND, sword);
            }
        }

        @Override
        public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                     @Nullable SpawnGroupData data) {
            SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
            if (risesOnSpawn(level, reason)) rise();
            return result;
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            rise.save(output);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            rise.load(input);
        }
    }
}
