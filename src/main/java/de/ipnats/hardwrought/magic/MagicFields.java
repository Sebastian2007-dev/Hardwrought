package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spells that stay where they were cast: a spiral's field, which acts again and again for a while,
 * and a square's trap, which lies on the ground until someone steps on it. Kept in memory only.
 */
final class MagicFields {
    /** A field acts this often, in ticks. */
    private static final int PULSE = 20;
    /** A field lasts this long before amplifiers, in ticks. */
    private static final int FIELD_LIFE = 100;
    /** A trap waits this long, in ticks, before its magic seeps away. */
    private static final int TRAP_LIFE = 20 * 60 * 2;
    /** How close a step must come to spring a trap, in blocks. */
    private static final double TRAP_REACH = 1.3;

    private static final class Field {
        final SpellEffects.Release spell;
        final Vec3 at;
        final boolean trap;
        final boolean follows;
        final long until;
        long next;
        long marked;

        Field(SpellEffects.Release spell, Vec3 at, boolean trap, long until) {
            this.spell = spell;
            this.at = at;
            this.trap = trap;
            // A field cast inside a circle goes along with its caster.
            this.follows = !trap && spell.placement() == Spell.Placement.SELF;
            this.until = until;
        }

        Vec3 position() {
            return follows ? spell.caster().position().add(0, 1, 0) : at;
        }
    }

    private static final List<Field> FIELDS = new ArrayList<>();

    private MagicFields() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Iterator<Field> it = FIELDS.iterator(); it.hasNext(); ) {
                Field field = it.next();
                if (field.spell.level().getServer() != server) continue;
                long now = field.spell.level().getGameTime();
                if (now >= field.until || (field.follows && field.spell.caster().isRemoved())) {
                    it.remove();
                    continue;
                }
                if (now < field.next) continue;
                if (field.trap) {
                    field.next = now + 5;
                    // The trap shows as a faint sigil, renewed while it lies there, so it fades soon after it is sprung.
                    if (now >= field.marked) {
                        field.marked = now + 40;
                        Fx.play(field.spell.level(), FxEffect.SIGIL, field.at, field.at, SpellEffects.color(field.spell), 2.3f, null);
                    }
                    if (sprung(field)) it.remove();
                } else {
                    field.next = now + PULSE;
                    pulse(field);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIELDS.removeIf(f -> f.spell.level().getServer() == server));
    }

    /** A spiral: the spell acts where it landed now and every second after, more weakly each time. */
    static void linger(SpellEffects.Release spell, Vec3 at) {
        long now = spell.level().getGameTime();
        FIELDS.add(new Field(spell.weaker(0.4), at, false, now + (long) (FIELD_LIFE * spell.duration())));
        Fx.play(spell.level(), FxEffect.RUNE_CIRCLE, at, at, SpellEffects.color(spell),
                (float) Math.min(2, (spell.area() + 1) / 2), null);
    }

    /** A square: the spell is laid on the ground and waits. */
    static void trap(SpellEffects.Release spell, Vec3 ground) {
        long now = spell.level().getGameTime();
        Field field = new Field(spell, ground, true, now + TRAP_LIFE);
        // A moment before it is armed, so the caster can step away from their own trap.
        field.next = now + 30;
        FIELDS.add(field);
        Fx.play(spell.level(), FxEffect.RUNE_CIRCLE, ground, ground, SpellEffects.color(spell), 0.6f, null);
    }

    /** How many fields and traps stand; for tests. */
    static int count() {
        return FIELDS.size();
    }

    private static void pulse(Field field) {
        Vec3 at = field.position();
        Fx.play(field.spell.level(), FxEffect.SIGIL, at.add(0, -0.95, 0), at, SpellEffects.color(field.spell), 1.2f, null);
        SpellEffects.impactEffect(field.spell, at, field.spell.area() * 0.6);
        // A field on the caster acts on them; one laid out acts on everything else in it.
        SpellEffects.strike(field.spell, at, field.spell.area(), field.follows);
    }

    private static boolean sprung(Field field) {
        Vec3 at = field.at;
        List<LivingEntity> near = field.spell.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(TRAP_REACH, 1.5, TRAP_REACH),
                entity -> entity.isAlive() && entity != field.spell.caster() && !entity.isSpectator());
        if (near.isEmpty()) return false;
        field.spell.level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1, 0.6f);
        SpellEffects.burst(field.spell, at.add(0, 0.5, 0));
        return true;
    }
}
