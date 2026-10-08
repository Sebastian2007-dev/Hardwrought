package de.ipnats.hardwrought.client.magic;

import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxLight;
import de.ipnats.hardwrought.client.fx.FxPresets;
import de.ipnats.hardwrought.magic.ShieldPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Shields as the player sees them: a real sphere around whoever raised one, drawn all round so the
 * caster sees it from inside too, that flares where it is struck and shatters when it breaks. It
 * lives exactly as long as the shield on the server ({@link ShieldPayload}).
 */
final class ShieldSpheres {
    private static final class Sphere {
        final FxDecal shell;
        final FxLight light;
        final int color;
        final float radius;
        /** For a shield that stands where it was raised: where. */
        final Vec3 fixed;
        float flash;
        boolean gone;

        Sphere(FxDecal shell, FxLight light, int color, float radius, Vec3 fixed) {
            this.shell = shell;
            this.light = light;
            this.color = color;
            this.radius = radius;
            this.fixed = fixed;
        }
    }

    private static final Map<Integer, Sphere> SPHERES = new HashMap<>();

    private ShieldSpheres() { }

    static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(ShieldPayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SPHERES.clear());
    }

    private static void receive(ShieldPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity owner = client.level.getEntity(payload.entity());
        switch (payload.event()) {
            case ShieldPayload.UP -> raise(payload, owner);
            case ShieldPayload.HIT -> {
                Sphere sphere = SPHERES.get(payload.entity());
                if (sphere != null) sphere.flash = 1;
            }
            case ShieldPayload.DOWN -> {
                Sphere sphere = SPHERES.remove(payload.entity());
                if (sphere != null) shatter(sphere, owner);
            }
            default -> { }
        }
    }

    private static void raise(ShieldPayload payload, Entity owner) {
        Vec3 fixed = payload.fixed() ? new Vec3(payload.x(), payload.y(), payload.z()) : null;
        if (owner == null && fixed == null) return;
        Sphere old = SPHERES.remove(payload.entity());
        if (old != null) {
            old.gone = true;
            old.shell.kill();
            old.light.kill();
        }
        FxEngine fx = FxClient.engine();
        Vec3 center = fixed != null ? fixed : middle(owner);
        // A little longer than the shield itself: the server's word that it is gone is what ends it.
        int life = payload.ticks() + 40;
        FxDecal shell = fx.decal(FxDecal.Kind.SPHERE, center, null).radius(0.2f, payload.radius()).grow(8)
                .life(life).color(payload.color()).alpha(0.75f).param(0).seed(payload.entity()).fade(0.02f, 0.98f);
        FxLight light = fx.light(center, payload.color(), payload.radius() * 3, life).intensity(0.7f);
        Sphere sphere = new Sphere(shell, light, payload.color(), payload.radius(), fixed);
        SPHERES.put(payload.entity(), sphere);
        FxPresets.play("sparkle", center, center, payload.color(), 1.2f, -1);
        fx.task((engine, age) -> {
            if (sphere.gone || (fixed == null && !owner.isAlive())) return false;
            if (fixed == null) {
                Vec3 c = middle(owner);
                shell.center(c);
                light.moveTo(c);
            }
            // A blow flares the whole sphere white for a moment.
            shell.param(sphere.flash);
            sphere.flash = Math.max(0, sphere.flash - 0.12f);
            return age < life;
        });
    }

    private static void shatter(Sphere sphere, Entity owner) {
        sphere.gone = true;
        sphere.shell.kill();
        sphere.light.kill();
        Vec3 center = sphere.fixed != null ? sphere.fixed : owner != null ? middle(owner) : null;
        if (center != null) FxPresets.play("frost_nova", center, center, sphere.color, sphere.radius / 1.7f, -1);
    }

    private static Vec3 middle(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2, 0);
    }
}
