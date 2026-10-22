package de.ipnats.hardwrought.steam;

import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.power.GasBurnerBlock;
import de.ipnats.hardwrought.smithing.ForgeBlock;
import de.ipnats.hardwrought.smithing.MetalStock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A boiler: a riveted iron drum with copper fire tubes through it, set over a fire. Water comes in by
 * pipe or bucket, steam leaves by gas line, at the pressure it has built up.
 *
 * <p>What happens inside is worked out tick by tick (see {@link Entity#tick}). The fire heats the
 * water and the iron; once the water reaches its boiling point, which climbs with the pressure, more
 * heat makes steam instead, and steam drawn off lets the pressure fall and the hot water flash to more.
 * A cold boiler therefore takes minutes to raise steam, and a hot one rides out a sudden draw.
 *
 * <p>And what goes wrong:
 * <ul>
 *   <li><b>Pressure.</b> A boiler holds {@value Entity#RATED_BAR} bar and bursts above
 *       {@value Entity#BURST_BAR}, less the more it is damaged. A {@link SafetyValveBlock} on top lets off
 *       what is above its setting; without one nothing stops it.</li>
 *   <li><b>Running dry.</b> Without water the fire heats bare iron: above {@value Entity#SCORCHING} °C it
 *       is ruined, and water fed into a red-hot boiler flashes to steam all at once and bursts it.</li>
 *   <li><b>Scale.</b> Every water but purified leaves scale on the tubes, which keeps the heat from the
 *       water and in the end overheats the iron under it. A pickaxe chips it off a cold boiler.</li>
 *   <li><b>Leaks.</b> A damaged boiler leaks steam, which scalds whoever stands by. Iron plates rivet
 *       a patch over the worst of it, on a boiler under 100 °C.</li>
 * </ul>
 */
public class BoilerBlock extends Block implements EntityBlock {
    /** Above the boiling point at no pressure: shows the steam about it and its shimmer. */
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    public static final BooleanProperty LEAKING = BooleanProperty.create("leaking");

    public BoilerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HOT, false).setValue(LEAKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HOT, LEAKING);
    }

    /**
     * The heat of what stands right under a boiler, in watts: its own firebox best, a gas burner nearly as
     * well, and the fires that are no fireboxes poorly.
     */
    public static double heatBelow(Level level, BlockPos boiler) {
        BlockPos at = boiler.below();
        BlockState below = level.getBlockState(at);
        if (level.getBlockEntity(at) instanceof FireboxBlock.Entity box) return box.watts();
        if (below.getBlock() instanceof GasBurnerBlock) return below.getValue(GasBurnerBlock.LIT) ? 8_000 : 0;
        if (below.getBlock() instanceof ForgeBlock) return below.getValue(ForgeBlock.LIT) ? 5_000 : 0;
        if (below.getBlock() instanceof AbstractFurnaceBlock) {
            return below.getOptionalValue(BlockStateProperties.LIT).orElse(false) ? 3_000 : 0;
        }
        if (below.getBlock() instanceof CampfireBlock) {
            return below.getOptionalValue(BlockStateProperties.LIT).orElse(false) ? 1_500 : 0;
        }
        if (below.is(Blocks.LAVA)) return 3_000;
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE)) return 2_000;
        if (below.is(Blocks.MAGMA_BLOCK)) return 1_000;
        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity boiler)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(ItemTags.PICKAXES)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            String said = boiler.descale();
            if (said.equals("descaled")) {
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            player.sendOverlayMessage(Component.translatable("message.hardwrought.boiler." + said));
            return InteractionResult.SUCCESS;
        }
        if (stack.is(MetalStock.plate("iron"))) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            String said = boiler.patch();
            if (said.equals("patched")) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.4f);
            }
            player.sendOverlayMessage(Component.translatable("message.hardwrought.boiler." + said));
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty() && FluidStorageUtil.interactWithFluidStorage(boiler.water, player, hand)) return InteractionResult.SUCCESS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** An empty hand reads its gauges. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity boiler)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.sendOverlayMessage(boiler.reading());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEAKING)) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 0.3 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0, 0.04, 0);
            if (random.nextInt(6) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.2f, 1.8f, false);
            }
        } else if (state.getValue(HOT) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 1.0,
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != SteamBlocks.BOILER_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends BlockEntity {
        public static final int WATER_BUCKETS = 4;
        /** The drum's room for steam, in units, and the pressure it stands at full. */
        public static final double STEAM_CAPACITY = 100, FULL_BAR = 20;
        /** What it is built to hold, and where a sound one bursts. */
        public static final double RATED_BAR = 10, BURST_BAR = 16;
        /** The heat one unit of steam carries, and the water it is made of: as a reactor's steam. */
        public static final double UNIT_JOULES = 4000, WATER_PER_UNIT = 2;
        /** Heat capacity of the iron, in J/K, and of every millibucket of water in it. */
        public static final double IRON_HEAT = 4000, WATER_HEAT = 2;
        /** What it loses through its lagging, in W for each degree above the air. */
        public static final double LOSS = 8, AIR = 20;
        /** Bare iron hotter than this is being ruined. */
        public static final int SCORCHING = 300;
        /** Water meeting iron hotter than this flashes to steam at once. */
        public static final int FLASH = 350;
        /** From this much damage on it leaks. */
        public static final double LEAKS_FROM = 0.25;
        /** What a patch of iron plate takes off its damage. */
        public static final double PATCH = 0.25;

        public final BoilerWater water = new BoilerWater(WATER_BUCKETS, this::setChanged);
        public final GasStore steam = new GasStore(STEAM_CAPACITY, FULL_BAR, false, this::setChanged);
        private double temperature = AIR;
        /** Scale on the tubes, 0 clean to 1 furred up. */
        private double scale;
        /** 0 sound to 1 ruined. */
        private double damage;
        /** Water boiled off but not yet taken out of the drum, in millibuckets. */
        private double boiled;
        private boolean venting;
        private long tick;

        public Entity(BlockPos pos, BlockState state) {
            super(SteamBlocks.BOILER_ENTITY, pos, state);
        }

        public double temperature() {
            return temperature;
        }

        public double pressure() {
            return steam.pressure();
        }

        public double scale() {
            return scale;
        }

        public double damage() {
            return damage;
        }

        public boolean venting() {
            return venting;
        }

        /** Where it bursts now: lower the more it has been damaged. */
        public double burstBar() {
            return BURST_BAR * (1.0 - 0.5 * damage);
        }

        /** The boiling point of its water at a pressure above the air's, in °C: 100 open, about 180 at 10 bar. */
        public static double boilingPoint(double bar) {
            return 100.0 * Math.pow(1.0 + Math.max(0, bar), 0.25);
        }

        public void setForTesting(double temperature, double scale, double damage) {
            this.temperature = temperature;
            this.scale = scale;
            this.damage = damage;
            setChanged();
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity boiler) {
            if (level instanceof ServerLevel server) boiler.tick(server, pos, state);
        }

        /** One tick of a boiler. */
        void tick(ServerLevel level, BlockPos pos, BlockState state) {
            tick++;
            double seconds = 1.0 / 20.0;
            long waterMb = water.millibuckets();
            double heat = heatBelow(level, pos);
            // Scale lies between the fire and the water: what it keeps from the water goes into the iron.
            double through = heat * (1.0 - 0.5 * scale);
            double capacity = IRON_HEAT + WATER_HEAT * waterMb;

            // Water on iron hotter than the flash point: all of it steam in an instant.
            if (waterMb > 0 && temperature > FLASH) {
                explode(level, pos, 2.0f + (float) ((temperature - FLASH) / 150.0) + waterMb / 2000.0f);
                return;
            }

            temperature += (through - LOSS * (temperature - AIR)) * seconds / capacity;
            if (waterMb > 0) {
                if (temperature > boilingPoint(steam.pressure())) {
                    double most = Math.min(steam.capacity() - steam.amount(), (waterMb - boiled) / WATER_PER_UNIT);
                    double units = flash(capacity, most);
                    if (units > 0) {
                        units = steam.produce(GasKind.STEAM, units);
                        temperature -= units * UNIT_JOULES / capacity;
                        boiled += units * WATER_PER_UNIT;
                        long whole = (long) Math.floor(boiled);
                        if (whole > 0) {
                            scale = Math.min(1.0, scale + water.boil(whole));
                            boiled -= whole;
                        }
                    }
                }
                // Furred tubes over a fire overheat the iron under the scale.
                if (scale > 0.5 && heat > 0) damage += (scale - 0.5) * 2e-5 * heat / FireboxBlock.Entity.WATTS;
            } else {
                boiled = 0;
                if (temperature > SCORCHING) damage += (temperature - SCORCHING) / 100.0 * 5e-4;
            }

            double bar = steam.pressure();
            vent(level, pos, bar);
            bar = steam.pressure();

            // Close to its limit the iron strains; past it, it gives way sooner or later.
            if (bar > 0.85 * burstBar()) damage += 1e-4;
            damage = Math.min(1.0, damage);
            if (bar > burstBar() && tick % 20 == 0 && level.getRandom().nextInt(3) == 0) {
                explode(level, pos, 1.5f + (float) (bar / 5.0) + waterMb / 2000.0f);
                return;
            }

            // A damaged boiler leaks at its seams, the more the worse it is and the higher it stands.
            boolean leaking = damage >= LEAKS_FROM && bar > 0.2;
            if (leaking) {
                steam.extract((damage - LEAKS_FROM) * 4.0 * bar / RATED_BAR * seconds);
                if (tick % 20 == 0) Scalding.leak(level, pos, bar);
            }

            boolean hot = temperature > 100;
            if (state.getValue(HOT) != hot || state.getValue(LEAKING) != leaking) {
                level.setBlock(pos, state.setValue(HOT, hot).setValue(LEAKING, leaking), Block.UPDATE_CLIENTS);
            }
            setChanged();
        }

        /**
         * How much steam the water gives off now: as much as brings it down to the boiling point of the
         * pressure that steam itself makes, and no more than {@code most}. Each unit taken cools the water
         * and raises the pressure, so the two meet; found by halving.
         */
        private double flash(double capacity, double most) {
            double perUnit = FULL_BAR / STEAM_CAPACITY;
            double low = 0, high = Math.max(0, most);
            for (int step = 0; step < 24; step++) {
                double units = (low + high) / 2;
                double after = temperature - units * UNIT_JOULES / capacity;
                if (after > boilingPoint(steam.pressure() + units * perUnit)) low = units;
                else high = units;
            }
            return low;
        }

        /**
         * The safety valve on top, if there is one, lets off what is above its setting. Once lifted it
         * blows the boiler down a little below it before it reseats, as real ones do.
         */
        private void vent(ServerLevel level, BlockPos pos, double bar) {
            BlockPos top = pos.above();
            BlockState valve = level.getBlockState(top);
            boolean lifting = false;
            if (valve.getBlock() instanceof SafetyValveBlock) {
                double setting = SafetyValveBlock.bar(valve);
                double down = venting ? setting - SafetyValveBlock.BLOWDOWN : setting;
                if (bar > down) {
                    double units = (bar - down) * STEAM_CAPACITY / FULL_BAR;
                    steam.extract(Math.min(units, SafetyValveBlock.FLOW));
                    lifting = steam.pressure() > down + 1e-6;
                    if (tick % 20 == 0) {
                        level.playSound(null, top, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.7f);
                    }
                }
                if (valve.getValue(SafetyValveBlock.LIFTING) != lifting) {
                    level.setBlock(top, valve.setValue(SafetyValveBlock.LIFTING, lifting), Block.UPDATE_CLIENTS);
                }
            }
            venting = lifting;
        }

        private void explode(ServerLevel level, BlockPos pos, float power) {
            double steamLeft = steam.amount();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Math.min(6.0f, power),
                    Level.ExplosionInteraction.BLOCK);
            Scalding.burst(level, pos, steamLeft + 20);
        }

        /** A pickaxe on a cold boiler: the scale chipped off. What it says to the player. */
        String descale() {
            if (temperature > 50) return "too_hot";
            if (scale < 0.01) return "clean";
            scale = 0;
            setChanged();
            return "descaled";
        }

        /** An iron plate riveted over a leak, on a boiler that has cooled enough to work on. */
        String patch() {
            if (temperature > 100 || steam.pressure() > 0.5) return "too_hot";
            if (damage < 0.01) return "sound";
            damage = Math.max(0, damage - PATCH);
            setChanged();
            return "patched";
        }

        Component reading() {
            return Component.translatable("message.hardwrought.boiler.reading",
                    (int) Math.round(temperature), String.format(java.util.Locale.ROOT, "%.1f", steam.pressure()),
                    water.millibuckets(), water.capacityMillibuckets(), (int) Math.round(scale * 100), (int) Math.round(damage * 100));
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            water.load(input, "water");
            steam.load(input, "steam");
            temperature = input.getDoubleOr("temperature", AIR);
            scale = input.getDoubleOr("scale", 0);
            damage = input.getDoubleOr("damage", 0);
            boiled = input.getDoubleOr("boiled", 0);
            venting = input.getBooleanOr("venting", false);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            water.save(output, "water");
            steam.save(output, "steam");
            output.putDouble("temperature", temperature);
            output.putDouble("scale", scale);
            output.putDouble("damage", damage);
            output.putDouble("boiled", boiled);
            output.putBoolean("venting", venting);
        }
    }
}
