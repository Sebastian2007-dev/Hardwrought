package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.SurvivalSnapshotPayload;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinition;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinitions;
import de.ipnats.hardwrought.core.registry.ItemWeightDefinitions;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Milestone-1 survival simulation. All mutations happen on the server thread. */
public final class SurvivalSystem {
    private static final Identifier MOVEMENT_MODIFIER = Hardwrought.id("carry_movement_penalty");
    private static final Identifier JUMP_MODIFIER = Hardwrought.id("carry_jump_penalty");
    private static final Identifier MINING_MODIFIER = Hardwrought.id("fatigue_mining_penalty");
    // Stamina is a long-term reserve: at ideal conditions a full bar supports about eight minutes of sprinting.
    private static final double SPRINT_STAMINA_PER_TICK = 0.010;
    private static final double SWIM_STAMINA_PER_TICK = 0.008;
    private static final double CLIMB_STAMINA_PER_TICK = 0.006;
    private static final double OVERLOAD_STAMINA_PER_TICK = 0.002;
    private static final double JUMP_STAMINA = 0.30;
    private static final double ATTACK_STAMINA = 0.50;
    private static final double MINED_BLOCK_STAMINA = 0.10;
    private static final double IDLE_RECOVERY_PER_TICK = 0.012;
    private static final double SLEEP_RECOVERY_PER_TICK = 0.025;
    private static boolean eventsInitialized;

    private final MinecraftServer server;
    private final CoreSaveData save;
    private final Set<UUID> outdoorSleepers = new HashSet<>();
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();
    private final Map<UUID, Double> sleepQuality = new HashMap<>();
    private final Map<UUID, ActivityLoad> activity = new HashMap<>();
    private float normalTickRate = 20.0f;
    private boolean acceleratingSleep;

    public SurvivalSystem(MinecraftServer server, CoreSaveData save, SimulationScheduler scheduler) {
        this.server = server;
        this.save = save;
        scheduler.register("hardwrought:survival_movement", SimulationTier.CRITICAL, this::tickMovement);
        scheduler.register("hardwrought:survival_metabolism", SimulationTier.MEDIUM, this::tickMetabolism);
    }

    public static void initializeEvents() {
        if (eventsInitialized) return;
        eventsInitialized = true;
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> false);
        EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayer player) {
                var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(player.level().getServer());
                if (runtime != null) runtime.survival().beginSleep(player, pos);
            }
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayer player) {
                var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(player.level().getServer());
                if (runtime != null) runtime.survival().endSleep(player.getUUID());
            }
        });
        EntitySleepEvents.ALLOW_BED.register((entity, pos, state, vanilla) -> {
            if (entity instanceof ServerPlayer player) {
                var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(player.level().getServer());
                if (runtime != null && runtime.survival().isOutdoorSleeper(player.getUUID())) return EventResult.ALLOW;
            }
            return EventResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(serverPlayer.level().getServer());
                if (runtime != null && !runtime.survival().spendStamina(serverPlayer, ATTACK_STAMINA)) {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(serverPlayer.level().getServer());
                if (runtime != null) runtime.survival().spendStamina(serverPlayer, MINED_BLOCK_STAMINA);
            }
        });
    }

    public PlayerVitals vitals(ServerPlayer player) { return save.vitals(player.getUUID()); }

    public boolean spendStamina(ServerPlayer player, double amount) {
        PlayerVitals current = vitals(player);
        if (current.stamina() < Math.min(2.0, amount)) {
            player.setSprinting(false);
            return false;
        }
        save.setVitals(player.getUUID(), current.withStamina(current.stamina() - amount));
        recordWork(player, amount * 0.75, amount * 0.008);
        return true;
    }

    public void consumeFood(ServerPlayer player, ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            FoodNutritionDefinition profile = server.getOrThrow(FoodNutritionDefinitions.KEY).get(id);
            save.setVitals(player.getUUID(), profile == null
                    ? vitals(player).eat(food.nutrition(), food.saturation())
                    : vitals(player).eat(profile));
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.is(Potions.WATER)) drink(player, 22.0);
    }

    public void drink(ServerPlayer player, double amount) {
        save.setVitals(player.getUUID(), vitals(player).drink(amount));
        sync(player, carriedWeight(player), ambientTemperature(player), quality(player));
    }

    public void toggleSleep(ServerPlayer player) {
        if (player.isSleeping()) {
            boolean outdoor = outdoorSleepers.contains(player.getUUID());
            outdoorSleepers.remove(player.getUUID());
            if (outdoor) player.stopSleeping();
            else player.stopSleepInBed(true, true);
            updateSleepAcceleration();
            return;
        }
        if (!player.onGround() || player.isInWater() || player.isPassenger()) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.sleep_unsafe"));
            return;
        }
        AABB danger = player.getBoundingBox().inflate(8, 5, 8);
        boolean monsters = !player.level().getEntities(player, danger, entity -> entity instanceof Monster).isEmpty();
        if (monsters) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.sleep_monsters"));
            return;
        }
        BlockPos pos = player.blockPosition();
        outdoorSleepers.add(player.getUUID());
        if (!player.startSleeping(pos)) {
            outdoorSleepers.remove(player.getUUID());
            sleepQuality.remove(player.getUUID());
            return;
        }
        updateSleepAcceleration();
    }

    public boolean isOutdoorSleeper(UUID id) { return outdoorSleepers.contains(id); }

    private void beginSleep(ServerPlayer player, BlockPos pos) {
        sleepQuality.put(player.getUUID(), calculateSleepQuality(player, pos));
        updateSleepAcceleration();
    }

    private void endSleep(UUID id) {
        outdoorSleepers.remove(id);
        sleepQuality.remove(id);
        updateSleepAcceleration();
    }

    public void disconnect(UUID id) {
        outdoorSleepers.remove(id);
        lastOnGround.remove(id);
        sleepQuality.remove(id);
        activity.remove(id);
        updateSleepAcceleration();
    }

    public void shutdown() {
        if (acceleratingSleep) server.tickRateManager().setTickRate(normalTickRate);
        outdoorSleepers.clear();
        sleepQuality.clear();
        acceleratingSleep = false;
    }

    private void tickMovement() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerVitals value = vitals(player);
            double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
            boolean active = horizontalSpeed > 0.02;
            double delta = 0;
            if (player.isSprinting() && active) delta -= SPRINT_STAMINA_PER_TICK;
            else if (player.isSwimming() && active) delta -= SWIM_STAMINA_PER_TICK;
            else if (player.onClimbable() && Math.abs(player.getDeltaMovement().y) > 0.01) {
                delta -= CLIMB_STAMINA_PER_TICK;
            }
            boolean wasOnGround = lastOnGround.getOrDefault(player.getUUID(), player.onGround());
            if (wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.1) {
                delta -= JUMP_STAMINA;
                recordWork(player, 0.20, 0.004);
            }
            lastOnGround.put(player.getUUID(), player.onGround());

            double carried = carriedWeight(player);
            double load = Math.max(0, carried / CarryWeight.BASE_CAPACITY_KG - 1.0);
            if (active && load > 0) delta -= OVERLOAD_STAMINA_PER_TICK * load;
            if (!active && !player.isSleeping()) {
                double recovery = IDLE_RECOVERY_PER_TICK * recoveryFactor(value, load);
                delta += recovery;
            }
            if (player.isSleeping()) {
                double quality = quality(player);
                double newFatigue = value.fatigue() - 0.006 * quality;
                value = new PlayerVitals(value.stamina() + SLEEP_RECOVERY_PER_TICK * quality,
                        value.hydration(), value.calories(),
                        value.protein(), value.carbohydrates(), value.fat(), value.micronutrients(),
                        newFatigue, value.bodyTemperature(), value.wetness()).normalized();
                if (value.fatigue() <= 0.05) {
                    boolean outdoor = outdoorSleepers.remove(player.getUUID());
                    if (outdoor) player.stopSleeping();
                    else player.stopSleepInBed(true, true);
                    updateSleepAcceleration();
                }
            }
            if (delta != 0) value = value.withStamina(value.stamina() + delta);
            if (value.stamina() <= 0.1) player.setSprinting(false);
            save.setVitals(player.getUUID(), value);
        }
    }

    private void tickMetabolism() {
        outdoorSleepers.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !player.isSleeping();
        });
        sleepQuality.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !player.isSleeping();
        });
        updateSleepAcceleration();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerVitals v = vitals(player);
            double carried = carriedWeight(player);
            double ambient = ambientTemperature(player);
            double wetness = player.isInWaterOrRain() ? Math.min(1, v.wetness() + 0.12) : Math.max(0, v.wetness() - 0.025);
            double insulation = 0;
            double armorMass = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = player.getItemBySlot(slot);
                if (!armor.isEmpty()) {
                    insulation += 0.12;
                    armorMass += CarryWeight.perItem(armor, server.getOrThrow(ItemWeightDefinitions.KEY));
                }
            }
            double environmentDelta = ambient - 20.0;
            double exposure = environmentDelta < 0 ? Math.max(0.35, 1.0 - insulation * 1.8)
                    : 1.0 + armorMass * 0.025;
            double targetBody = 37.0 + environmentDelta * 0.055 * exposure;
            if (ambient < 20) targetBody -= wetness * 1.2;
            if (player.isSprinting() || player.isSwimming()) targetBody += 0.45 + armorMass * 0.015;
            double body = v.bodyTemperature() + (targetBody - v.bodyTemperature()) * 0.020;
            ActivityLoad work = activity.remove(player.getUUID());
            double workEnergy = work == null ? 0 : work.energy;
            double workWater = work == null ? 0 : work.water;
            double activityWater = player.isSprinting() ? 0.045 : (player.isSwimming() ? 0.035 : 0);
            double heatWater = Math.max(0, ambient - 28) * 0.006;
            double armorWater = armorMass * 0.0025;
            double excessLoad = Math.max(0, carried / CarryWeight.BASE_CAPACITY_KG - 1.0);
            double coldEnergy = Math.max(0, 10 - ambient) * 0.08;
            double energyUse = 0.65 + workEnergy + coldEnergy + excessLoad * 0.8;
            if (player.isSprinting()) energyUse += 1.15;
            else if (player.isSwimming()) energyUse += 0.9;
            double fatigue = v.fatigue() + (player.isSleeping() ? 0 : 0.035 + workEnergy * 0.002);
            PlayerVitals next = new PlayerVitals(v.stamina(), v.hydration() - 0.035 - activityWater - heatWater,
                    v.calories() - energyUse, v.protein() - 0.006,
                    v.carbohydrates() - 0.012 - energyUse * 0.012, v.fat() - 0.006 - coldEnergy * 0.004,
                    v.micronutrients() - 0.002, fatigue, body, wetness).normalized();
            next = new PlayerVitals(next.stamina(), next.hydration() - workWater - armorWater,
                    next.calories(), next.protein(), next.carbohydrates(), next.fat(), next.micronutrients(),
                    next.fatigue(), next.bodyTemperature(), next.wetness()).normalized();
            save.setVitals(player.getUUID(), next);
            if (save.ticks() % 200 == 0 && (next.bodyTemperature() < 34.0 || next.bodyTemperature() > 40.5)) {
                player.hurtServer(player.level(), next.bodyTemperature() < 34.0
                        ? player.level().damageSources().freeze() : player.level().damageSources().hotFloor(), 1.0f);
            }
            applyPenalties(player, next, carried);
            sync(player, carried, ambient, quality(player));
        }
    }

    private double recoveryFactor(PlayerVitals v, double load) {
        double hydration = 0.25 + 0.75 * v.hydration() / 100.0;
        double energy = 0.25 + 0.75 * v.calories() / PlayerVitals.MAX_CALORIES;
        double rest = 1.0 - 0.65 * v.fatigue() / 100.0;
        double thermal = Math.max(0.25, 1.0 - Math.abs(v.bodyTemperature() - 37.0) * 0.3);
        return hydration * energy * rest * thermal / (1.0 + load);
    }

    private void applyPenalties(ServerPlayer player, PlayerVitals v, double carried) {
        double load = Math.max(0, carried / CarryWeight.BASE_CAPACITY_KG - 1.0);
        double exhaustion = (100.0 - v.stamina()) / 100.0;
        double speedPenalty = -Math.min(0.55, load * 0.35 + exhaustion * 0.18);
        double jumpPenalty = -Math.min(0.65, load * 0.45 + exhaustion * 0.22);
        double miningPenalty = -Math.min(0.75, exhaustion * 0.45 + v.fatigue() / 300.0);
        updateModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_MODIFIER, speedPenalty);
        updateModifier(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP_MODIFIER, jumpPenalty);
        updateModifier(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), MINING_MODIFIER, miningPenalty);
    }

    private static void updateModifier(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute == null) return;
        attribute.removeModifier(id);
        if (amount != 0) attribute.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private double ambientTemperature(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        double temperature = 14.0 + (level.getBiome(pos).value().getBaseTemperature() - 0.8) * 18.0;
        temperature -= Math.max(0, pos.getY() - 64) * 0.0065;
        if (level.isDarkOutside()) temperature -= 4.0;
        else if (level.isBrightOutside() && level.canSeeSky(pos.above()) && !level.isRainingAt(pos)) temperature += 3.0;
        if (level.isRainingAt(pos)) temperature -= 3.0;
        if (player.isInWater()) temperature -= 6.0;
        boolean heat = BlockPos.betweenClosedStream(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))
                .map(level::getBlockState).anyMatch(SurvivalSystem::isHeatSource);
        if (heat) temperature += 12.0;
        return PlayerVitals.clamp(temperature, -35, 55);
    }

    private static boolean isHeatSource(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.LAVA);
    }

    private double calculateSleepQuality(ServerPlayer player, BlockPos pos) {
        double quality = 0.45;
        if (!player.level().canSeeSky(pos.above())) quality += 0.20;
        BlockState sleepingState = player.level().getBlockState(pos);
        BlockState surface = player.level().getBlockState(pos.below());
        if (sleepingState.getBlock() instanceof AbstractBedBlock) quality += 0.30;
        if (surface.is(BlockTags.WOOL) || surface.is(BlockTags.WOOL_CARPETS)) quality += 0.18;
        else if (surface.is(BlockTags.DIRT)) quality += 0.08;
        else if (surface.is(BlockTags.PLANKS) || surface.is(BlockTags.LOGS)) quality += 0.04;
        else if (surface.is(BlockTags.BASE_STONE_OVERWORLD)) quality -= 0.08;
        double ambient = ambientTemperature(player);
        quality -= Math.min(0.25, Math.abs(ambient - 18) / 80.0);
        quality -= vitals(player).wetness() * 0.20;
        AABB safetyArea = player.getBoundingBox().inflate(16, 8, 16);
        long hostiles = player.level().getEntities(player, safetyArea, entity -> entity instanceof Monster).size();
        quality -= Math.min(0.25, hostiles * 0.06);
        AABB noiseArea = player.getBoundingBox().inflate(7, 4, 7);
        long noisyEntities = player.level().getEntities(player, noiseArea,
                entity -> entity instanceof LivingEntity && !(entity instanceof Monster) && !entity.isSilent()).size();
        quality -= Math.min(0.15, noisyEntities * 0.025);
        return PlayerVitals.clamp(quality, 0.15, 1.0);
    }

    private double quality(ServerPlayer player) { return sleepQuality.getOrDefault(player.getUUID(), 0.5); }

    private void updateSleepAcceleration() {
        boolean active = !sleepQuality.isEmpty();
        float current = server.tickRateManager().tickrate();
        if (active) {
            if (!acceleratingSleep) {
                acceleratingSleep = true;
                normalTickRate = current;
            }
            int online = Math.max(1, server.getPlayerList().getPlayerCount());
            double fraction = Math.min(1.0, sleepQuality.size() / (double) online);
            float desired = (float) (normalTickRate + (100.0 - normalTickRate) * fraction);
            if (Math.abs(current - desired) > 0.01f) server.tickRateManager().setTickRate(desired);
        } else if (acceleratingSleep) {
            acceleratingSleep = false;
            server.tickRateManager().setTickRate(normalTickRate);
        }
    }

    private double carriedWeight(ServerPlayer player) {
        return CarryWeight.calculate(player, server.getOrThrow(ItemWeightDefinitions.KEY));
    }

    private void recordWork(ServerPlayer player, double energy, double water) {
        ActivityLoad value = activity.computeIfAbsent(player.getUUID(), ignored -> new ActivityLoad());
        value.energy += energy;
        value.water += water;
    }

    private static final class ActivityLoad {
        private double energy;
        private double water;
    }

    private void sync(ServerPlayer player, double carried, double ambient, double quality) {
        if (!ServerPlayNetworking.canSend(player, SurvivalSnapshotPayload.TYPE)) return;
        PlayerVitals v = vitals(player);
        double nutrition = ((v.protein() / 120.0) + (v.carbohydrates() / 360.0)
                + (v.fat() / 120.0) + (v.micronutrients() / 100.0)) * 25.0;
        ServerPlayNetworking.send(player, new SurvivalSnapshotPayload(v.stamina(), v.hydration(), v.calories(),
                PlayerVitals.clamp(nutrition, 0, 100), v.fatigue(), v.bodyTemperature(), ambient,
                carried, CarryWeight.BASE_CAPACITY_KG, player.isSleeping(), quality));
    }
}
