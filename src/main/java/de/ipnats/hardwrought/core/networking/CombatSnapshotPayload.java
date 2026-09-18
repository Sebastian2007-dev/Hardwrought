package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.combat.CombatDamageType;
import de.ipnats.hardwrought.combat.CombatEvent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Display-only combat feedback. The client never decides an outcome, it only renders this. */
public record CombatSnapshotPayload(CombatEvent event, CombatDamageType damageType, float damage,
                                    int parryWindowTicks) implements CustomPacketPayload {
    public static final Type<CombatSnapshotPayload> TYPE = new Type<>(Hardwrought.id("combat_snapshot_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public CombatSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            return new CombatSnapshotPayload(CombatEvent.byOrdinal(buffer.readByte()),
                    damageTypeByOrdinal(buffer.readByte()), buffer.readFloat(), buffer.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CombatSnapshotPayload payload) {
            buffer.writeByte(payload.event.ordinal());
            buffer.writeByte(payload.damageType.ordinal());
            buffer.writeFloat(payload.damage);
            buffer.writeVarInt(payload.parryWindowTicks);
        }
    };

    public CombatSnapshotPayload {
        if (event == null || damageType == null) throw new IllegalArgumentException("Combat event and type are required");
        if (!Float.isFinite(damage) || damage < 0 || damage > 100_000) {
            throw new IllegalArgumentException("Invalid reported damage");
        }
        if (parryWindowTicks < 0 || parryWindowTicks > 40) {
            throw new IllegalArgumentException("Invalid parry window");
        }
    }

    private static CombatDamageType damageTypeByOrdinal(int ordinal) {
        CombatDamageType[] values = CombatDamageType.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown damage type: " + ordinal);
        }
        return values[ordinal];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
