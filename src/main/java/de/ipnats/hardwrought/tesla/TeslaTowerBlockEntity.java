package de.ipnats.hardwrought.tesla;

import de.ipnats.hardwrought.carried.Cells;
import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import de.ipnats.hardwrought.haven.ApplianceBlockEntity;
import de.ipnats.hardwrought.haven.HavenBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * A Tesla tower (stage E6), the foot of it: fed at 960 volts, it charges the capacitor bank beside it
 * and fires its resonant coil through the spark gap, so that the top load, four blocks up, sings with
 * power that needs no wire. It works only built whole — three resonant coils on the foot, the top load
 * on them, a capacitor bank against the foot.
 *
 * <ul>
 *   <li><b>Wireless charge</b>: every cell a player carries within {@link #REACH} — in a device, a lining,
 *       or loose — takes {@link #CHARGE_WATTS} from it.</li>
 *   <li><b>Wireless lamps</b>: an electric light within its reach with no line to it is lit from the
 *       tower, at its 25 watts.</li>
 *   <li><b>Defence</b>: a monster within {@link #DEFENCE} is struck by an arc for {@link #STRIKE_DAMAGE},
 *       at {@link #STRIKE_JOULES} a strike. Under load the bank runs down and the tower draws more from
 *       the line: the lights on it dim, and the player sees what the defence costs.</li>
 *   <li><b>Danger</b>: whoever stands within {@link #TOP_REACH} of the top load in metal armour draws arcs
 *       too; and in a thunderstorm the tower calls the lightning down on itself.</li>
 * </ul>
 */
public class TeslaTowerBlockEntity extends ApplianceBlockEntity {
    public static final double REACH = 24.0, DEFENCE = 16.0, TOP_REACH = 4.0;
    public static final double BANK_JOULES = 20_000.0;
    public static final double CHARGE_WATTS = 20.0;
    public static final double STRIKE_JOULES = 1500.0;
    public static final float STRIKE_DAMAGE = 8.0f;
    /** How many monsters it strikes in a second at most. */
    public static final int STRIKES_PER_SECOND = 3;
    public static final int COILS = 3;

    private double bank;
    private boolean whole;
    private int struck;

    public TeslaTowerBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.TESLA_TOWER_ENTITY, pos, state);
    }

    /** What the capacitor bank holds, in watt-seconds. */
    public double bank() {
        return bank;
    }

    /** Whether it is built whole. */
    public boolean whole() {
        return whole;
    }

    /** How many it struck in its last second. */
    public int struck() {
        return struck;
    }

    /** Where the top load stands, four blocks up. */
    public BlockPos topLoad() {
        return worldPosition.above(COILS + 1);
    }

    /** Three coils on the foot, the top load on them, a capacitor bank beside the foot. */
    public boolean built() {
        if (level == null) return false;
        for (int i = 1; i <= COILS; i++) if (!level.getBlockState(worldPosition.above(i)).is(TeslaBlocks.RESONANT_COIL)) return false;
        if (!level.getBlockState(topLoad()).is(TeslaBlocks.TOP_LOAD)) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(worldPosition.relative(side)).is(TeslaBlocks.CAPACITOR_BANK)) return true;
        }
        return false;
    }

    /** Takes watt-seconds out of the bank for something it powers without wire. False where the bank is short. */
    public boolean give(double joules) {
        if (!whole || bank < joules) return false;
        bank -= joules;
        setChanged();
        return true;
    }

    /** Charges the bank as this much power from the line would. For tests too. */
    public void fill(double joules) {
        bank = Math.min(BANK_JOULES, bank + joules);
        setChanged();
    }

    @Override
    protected boolean wants() {
        return whole && bank < BANK_JOULES - 1.0;
    }

    @Override
    protected void work(ServerLevel level, double power) {
        boolean was = whole;
        whole = built();
        if (whole != was) electricDirty = true;
        struck = 0;
        if (!whole) {
            TeslaTowers.down(level, worldPosition);
            return;
        }
        if (power > 0.0) fill(power * appliance().ratedWatts() * (WORK_TICKS / 20.0));
        if (bank <= 0.0) return;
        TeslaTowers.up(level, this);
        Vec3 top = Vec3.atCenterOf(topLoad());
        defend(level, top);
        charge(level);
        danger(level, top);
        if (level.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 6, 0.6, 0.6, 0.6, 0.2);
        }
    }

    private void defend(ServerLevel level, Vec3 top) {
        List<Monster> near = level.getEntitiesOfClass(Monster.class, new AABB(worldPosition).inflate(DEFENCE),
                monster -> monster.isAlive() && monster.position().distanceTo(Vec3.atCenterOf(worldPosition)) <= DEFENCE);
        for (Monster monster : near) {
            if (struck >= STRIKES_PER_SECOND || !give(STRIKE_JOULES)) break;
            arc(level, top, monster, STRIKE_DAMAGE);
            struck++;
        }
    }

    private void charge(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(Vec3.atCenterOf(worldPosition)) > REACH) continue;
            boolean changed = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                ItemStack cell = Cells.cellOf(stack);
                if (cell.isEmpty() || Cells.charge(cell) >= Cells.capacity(cell) - 1.0) continue;
                if (!give(CHARGE_WATTS * (WORK_TICKS / 20.0) / Cells.CHARGE_EFFICIENCY)) return;
                Cells.fill(stack, CHARGE_WATTS * (WORK_TICKS / 20.0));
                changed = true;
            }
            if (changed) player.getInventory().setChanged();
        }
    }

    /** Metal near the top load draws arcs; and the tower calls the lightning in a storm. */
    private void danger(ServerLevel level, Vec3 top) {
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator() || player.position().distanceTo(top) > TOP_REACH + 1.5) continue;
            if (wearsMetal(player) && level.getRandom().nextInt(4) == 0 && give(STRIKE_JOULES / 3)) arc(level, top, player, 4.0f);
        }
        if (level.isThundering() && level.getRandom().nextInt(30) == 0) {
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(top.x, top.y, top.z);
                level.addFreshEntity(bolt);
            }
        }
    }

    /** Whether anything metal is worn: armour that is not leather, cloth or a lining. */
    public static boolean wearsMetal(LivingEntity wearer) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = wearer.getItemBySlot(slot);
            Equippable equippable = worn.get(DataComponents.EQUIPPABLE);
            if (equippable == null || worn.isEmpty()) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(worn.getItem()).getPath();
            if (!id.startsWith("leather") && !id.contains("mantle") && !id.contains("wool") && !id.contains("cloth")) return true;
        }
        return false;
    }

    private void arc(ServerLevel level, Vec3 from, LivingEntity target, float damage) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int steps = Math.max(4, (int) (from.distanceTo(to) * 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 at = from.lerp(to, t).add((level.getRandom().nextDouble() - 0.5) * 0.4, (level.getRandom().nextDouble() - 0.5) * 0.4,
                    (level.getRandom().nextDouble() - 0.5) * 0.4);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.ELECTROCUTION));
        target.hurtServer(level, source, damage);
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.6f, 1.8f);
    }

    /** Its foot stands in porcelain: 960 volts in the rain does not flash over. */
    @Override
    public double insulationVolts() {
        return de.ipnats.hardwrought.electricity.Electricity.PORCELAIN_VOLTS;
    }

    @Override
    public void setRemoved() {
        if (level != null) TeslaTowers.down(level, worldPosition);
        super.setRemoved();
    }

    @Override
    protected Component state() {
        if (!whole) return Component.translatable("message.hardwrought.meter.tesla.unbuilt");
        return Component.translatable("message.hardwrought.meter.tesla", String.format(Locale.ROOT, "%.0f", bank / BANK_JOULES * 100.0), struck);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        bank = input.getDoubleOr("bank", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("bank", bank);
    }
}
