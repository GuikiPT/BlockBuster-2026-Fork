package mchorse.metamorph.api.creative;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;

/**
 * SEAM(P54/P55/P60): creative-morph network dispatch.
 *
 * <p>In legacy Metamorph the creative category classes talk directly to the
 * client {@code Dispatcher} (sending {@code PacketMorph},
 * {@code PacketSelectMorph}, {@code PacketAcquireMorph},
 * {@code PacketClearAcquired}, {@code PacketSyncMorph},
 * {@code PacketRemoveMorph}) and to {@code Metamorph.proxy.canUse}. Those
 * packet classes and the client proxy belong to the network / keybind phases
 * that have not yet landed in this tree, so the data-model classes route their
 * side effects through this seam instead of referencing packets directly.</p>
 *
 * <p>The default installed implementation is {@link Noop} (pure data model, no
 * networking) which keeps the model headless-testable. The network phase wires
 * a real implementation into {@link CreativeMorphNetwork#INSTANCE}.</p>
 */
public interface ICreativeMorphNetwork
{
    /** Mirrors {@code Metamorph.proxy.canUse(player)} (OP / creative gate). */
    boolean canUse(PlayerEntity player);

    /** {@code PacketMorph} — morph by full NBT. */
    void morph(AbstractMorph morph);

    /** {@code PacketSelectMorph(index)} — morph by acquired index. */
    void selectMorph(int index);

    /** {@code PacketAcquireMorph(morph, false)}. */
    void acquireMorph(AbstractMorph morph);

    /** {@code PacketClearAcquired}. */
    void clearAcquired();

    /** {@code PacketSyncMorph(morph, index)}. */
    void syncMorph(AbstractMorph morph, int index);

    /** {@code PacketRemoveMorph(index)}. */
    void removeMorph(int index);

    /**
     * {@code PacketKeybind(index, keybind)} — assign a survival hotkey to the
     * acquired morph at {@code index}. Used by the survival screen keybind
     * capture field (roadmap P61).
     */
    void keybind(int index, int keybind);

    /**
     * {@code PacketFavorite(index)} — toggle the favorite flag of the acquired
     * morph at {@code index}. Used by the survival screen favorite toggle
     * (roadmap P61).
     */
    void favorite(int index);

    /**
     * No-op default used by the headless data model. Every method is inert;
     * {@link #canUse(PlayerEntity)} returns {@code false} so keybind-triggered
     * morphs simply do nothing until the network phase installs a real handler.
     */
    class Noop implements ICreativeMorphNetwork
    {
        public boolean canUse(PlayerEntity player)
        {
            return false;
        }

        public void morph(AbstractMorph morph)
        {}

        public void selectMorph(int index)
        {}

        public void acquireMorph(AbstractMorph morph)
        {}

        public void clearAcquired()
        {}

        public void syncMorph(AbstractMorph morph, int index)
        {}

        public void removeMorph(int index)
        {}

        public void keybind(int index, int keybind)
        {}

        public void favorite(int index)
        {}
    }
}
