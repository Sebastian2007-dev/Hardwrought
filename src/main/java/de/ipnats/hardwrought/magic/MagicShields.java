package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shield magic (magic specification section 25): a sphere of force that moves with its caster and
 * stops things crossing it — either way. Mobs cannot walk in, blows and arrows from outside stop at
 * it, and the caster's own blows and arrows stop at it just the same, from inside. It holds a
 * certain amount of harm and then breaks.
 *
 * <p>It is drawn as an empty circle. Element runes drawn beside it give it their element: whoever
 * strikes it from outside feels that element, weakly — a fire shield burns, a wind shield throws back.
 *
 * <p>With a spiral it lingers: it stays where it was raised, larger and longer, a dome to stand in
 * rather than armour to wear. Players come and go through it freely; nothing else walks in.
 */
public final class MagicShields {
    /** How long a plain shield stands, in ticks; Earth and Dark among its runes make it last longer. */
    static final int LIFE = 140;

    private static final class Field {
        final ServerLevel level;
        final ServerPlayer owner;
        /** What it does to those who strike it, if it has an element. */
        final SpellEffects.Release element;
        final double radius;
        final int color;
        final long until;
        /** For a fixed shield: where it stands, and the number the clients know it by. */
        final Vec3 anchor;
        final int id;
        double strength;

        Field(ServerLevel level, ServerPlayer owner, SpellEffects.Release element, double radius, int color, long until,
              double strength, Vec3 anchor, int id) {
            this.level = level;
            this.owner = owner;
            this.element = element;
            this.color = color;
            this.radius = radius;
            this.until = until;
            this.strength = strength;
            this.anchor = anchor;
            this.id = id;
        }

        Vec3 center() {
            if (anchor != null) return anchor;
            return owner.isRemoved() || owner.level() != level ? null
                    : owner.position().add(0, owner.getBbHeight() / 2, 0);
        }

        boolean inside(Entity entity) {
            Vec3 c = center();
            return c != null && entity.position().add(0, entity.getBbHeight() / 2, 0).distanceToSqr(c) < radius * radius;
        }
    }

    private static final List<Field> FIELDS = new ArrayList<>();
    /** Fixed shields are named by negative numbers, so they never clash with an entity's. */
    private static int nextFixed = -1;

    private MagicShields() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Iterator<Field> it = FIELDS.iterator(); it.hasNext(); ) {
                Field field = it.next();
                if (field.level.getServer() != server) continue;
                Vec3 center = field.center();
                if (center == null || field.level.getGameTime() >= field.until || field.strength <= 0) {
                    it.remove();
                    tell(field, ShieldPayload.DOWN, 0);
                    continue;
                }
                hold(field, center);
            }
        });
        // A blow crossing the sphere, from either side, lands on the sphere instead.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            Field field = across(victim, source);
            if (field == null) return true;
            weaken(field, amount);
            if (field.element != null && source.getEntity() instanceof LivingEntity attacker && !field.inside(attacker)) {
                SpellEffects.payload(field.element, attacker, 0.5, field.center());
            }
            return false;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> FIELDS.removeIf(field -> field.level.getServer() == server));
    }

    /** Raises a shield around a player, replacing one they already have; core may be {@code null}. */
    static void raise(ServerLevel level, ServerPlayer caster, double power, double duration, Rune core, boolean inverted) {
        raise(level, caster, power, duration, core, inverted, null);
    }

    /**
     * Raises a shield: around a player, or — given a point — standing there, larger and longer. A
     * caster has one of each at most; a new one replaces the old.
     */
    static void raise(ServerLevel level, ServerPlayer caster, double power, double duration, Rune core, boolean inverted,
                      Vec3 at) {
        boolean fixed = at != null;
        for (Field old : FIELDS) if (old.owner == caster && (old.anchor != null) == fixed) tell(old, ShieldPayload.DOWN, 0);
        FIELDS.removeIf(field -> field.owner == caster && (field.anchor != null) == fixed);
        float scale = (float) Math.max(0.9, Math.min(1.6, 0.9 + 0.2 * power));
        SpellEffects.Release element = core == null ? null : new SpellEffects.Release(level, caster, core, inverted,
                Spell.Placement.SELF, caster.getLookAngle(), 0, false, false, false, false, power, 2, duration);
        int color = element == null ? 0x6FB8FF : SpellEffects.color(element);
        int life = (int) (LIFE * duration * (fixed ? 3 : 1));
        Field field = new Field(level, caster, element, (fixed ? 3.6 : 1.7) * scale, color, level.getGameTime() + life,
                8 * power * duration * (fixed ? 1.5 : 1), at, fixed ? nextFixed-- : caster.getId());
        FIELDS.add(field);
        tell(field, ShieldPayload.UP, life);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    /** Whether a player stands in a shield of their own; for tests. */
    public static boolean shielded(ServerPlayer player) {
        return FIELDS.stream().anyMatch(field -> field.owner == player && field.anchor == null);
    }

    /** Where a player's fixed shield stands, or {@code null}; for tests. */
    public static Vec3 fixedShield(ServerPlayer player) {
        return FIELDS.stream().filter(field -> field.owner == player && field.anchor != null).map(field -> field.anchor)
                .findFirst().orElse(null);
    }

    private static void hold(Field field, Vec3 center) {
        double r = field.radius;
        AABB box = new AABB(center, center).inflate(r + 3);
        // Nothing alive walks in: whatever is inside that is not the caster is put back out.
        for (LivingEntity entity : field.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != field.owner && !(field.anchor != null && e instanceof net.minecraft.world.entity.player.Player))) {
            Vec3 from = center;
            Vec3 at = entity.position().add(0, entity.getBbHeight() / 2, 0);
            double d = at.distanceTo(from);
            if (d >= r) continue;
            Vec3 out = d < 1e-3 ? new Vec3(1, 0, 0) : at.subtract(from).scale(1 / d);
            SpellEffects.push(entity, out.multiply(1, 0.2, 1).scale(0.35));
        }
        // Anything flying across the surface, in or out, stops there.
        for (Projectile projectile : field.level.getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
            double before = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld).distanceTo(center) - r;
            double after = projectile.position().distanceTo(center) - r;
            if (before * after <= 0 && Math.abs(before - after) > 1e-4) {
                projectile.discard();
                weaken(field, 3);
            }
        }
    }

    /** The shield a blow from source to victim would have to cross, if any. */
    private static Field across(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (attacker == null || attacker == victim) return null;
        for (Field field : FIELDS) {
            if (field.level != victim.level()) continue;
            if (field.inside(victim) != field.inside(attacker)) return field;
        }
        return null;
    }

    private static void weaken(Field field, double amount) {
        field.strength -= amount;
        Vec3 center = field.center();
        if (center == null) return;
        if (field.strength <= 0) {
            field.level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1, 0.7f);
        } else {
            field.level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 1.2f);
            tell(field, ShieldPayload.HIT, 0);
        }
    }

    /** Tells every client near the shield what became of it, so the sphere they draw follows it exactly. */
    private static void tell(Field field, byte event, int ticks) {
        Vec3 c = field.anchor != null ? field.anchor : field.owner.position();
        ShieldPayload payload = new ShieldPayload(field.id, (float) field.radius, field.color, ticks, event, field.anchor != null,
                c.x, c.y, c.z);
        for (ServerPlayer player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.around(field.level, c, Fx.RANGE)) {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, ShieldPayload.TYPE)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
