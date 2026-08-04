package mchorse.metamorph.capabilities.morphing;

import java.util.ArrayList;
import java.util.List;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Morphing capability persistence (roadmap P52) — 1:1 port of Metamorph 1.4's
 * {@code MorphingStorage} (Forge's {@code Capability.IStorage} becomes two
 * static helpers, mirroring P112's {@code RecordingStorage}).
 *
 * <p><b>Load-bearing bugs preserved (see plan/S04 quirk ledger):</b></p>
 * <ul>
 * <li>{@code writeNBT} writes the ratio under lowercase {@code "lastHealthRatio"}
 * but {@code readNBT} reads capital {@code "LastHealthRatio"} — the ratio
 * therefore <b>never survives a save</b>. Kept verbatim; {@code
 * MorphingComponentNBTTest} documents it.</li>
 * <li>{@code "Morphs"} (acquired list) is always written, even when empty;
 * {@code "Morph"} (current) only when morphed.</li>
 * <li>{@code readNBT} skips the current-morph restore when the whole compound is
 * empty (legacy {@code !tag.hasNoTags()} → {@code !tag.isEmpty()}).</li>
 * </ul>
 *
 * <p>Restoration goes through {@code MorphManager.INSTANCE.morphFromNBT} with
 * {@code setCurrentMorph(morph, null, true)} (force, null player) exactly like
 * legacy — a total reader (unknown/blacklisted morph names resolve to
 * {@code null} and are dropped, never crashing).</p>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/.../capabilities/morphing/MorphingStorage.java
 */
public class MorphingStorage
{
    /**
     * The player-{@code .dat} compound the capability persists under — legacy
     * {@code CapabilityHandler.MORPHING_CAP}'s {@code ForgeCaps} key, written
     * top-level on 1.20.4 by {@code PlayerEntityMorphDataMixin}. Public so
     * {@code RecordPlayer.checkAndSpawn} can strip it out of a record's
     * {@code playerData} before restoring — the replay morph, not the recorded
     * morph state, owns a fake player's appearance.
     */
    public static final String MORPHING_KEY = "metamorph:morphing_capability";

    /**
     * Legacy {@code writeNBT}: serialize the whole capability into {@code tag}.
     */
    public static void writeNBT(IMorphing instance, NbtCompound tag)
    {
        NbtList acquired = new NbtList();

        /* NOTE: lowercase "lastHealthRatio" is intentional — readNBT reads
         * capital "LastHealthRatio", so the ratio never round-trips (legacy
         * bug, kept for parity). */
        tag.putFloat("lastHealthRatio", instance.getLastHealthRatio());
        tag.putBoolean("HasSquidAir", instance.getHasSquidAir());
        tag.putInt("SquidAir", instance.getSquidAir());
        tag.putFloat("lastHealth", instance.getLastHealth());

        if (instance.getCurrentMorph() != null)
        {
            NbtCompound morph = new NbtCompound();
            instance.getCurrentMorph().toNBT(morph);

            tag.put("Morph", morph);
        }

        /* The acquired list is written even when empty. */
        tag.put("Morphs", acquired);

        for (AbstractMorph acquiredMorph : instance.getAcquiredMorphs())
        {
            NbtCompound acquiredTag = new NbtCompound();

            acquiredMorph.toNBT(acquiredTag);
            acquired.add(acquiredTag);
        }
    }

    /**
     * Legacy {@code readNBT}: restore the capability from {@code tag} (total —
     * a {@code null}/empty compound leaves defaults).
     */
    public static void readNBT(IMorphing instance, NbtCompound tag)
    {
        if (tag == null)
        {
            return;
        }

        NbtList acquired = tag.getList("Morphs", NbtElement.COMPOUND_TYPE);
        NbtCompound morphTag = tag.getCompound("Morph");

        /* Read capital "LastHealthRatio" (legacy bug — writer emits lowercase). */
        instance.setLastHealthRatio(tag.getFloat("LastHealthRatio"));
        instance.setHasSquidAir(tag.getBoolean("HasSquidAir"));
        instance.setSquidAir(tag.getInt("SquidAir"));
        instance.setLastHealth(tag.getFloat("lastHealth"));

        if (!tag.isEmpty())
        {
            instance.setCurrentMorph(MorphManager.INSTANCE.morphFromNBT(morphTag), null, true);
        }

        List<AbstractMorph> acquiredMorphs = new ArrayList<AbstractMorph>();

        if (!acquired.isEmpty())
        {
            for (int i = 0; i < acquired.size(); i++)
            {
                NbtCompound acquiredTag = acquired.getCompound(i);
                AbstractMorph morph = MorphManager.INSTANCE.morphFromNBT(acquiredTag);

                if (morph != null)
                {
                    acquiredMorphs.add(morph);
                }
            }

            instance.setAcquiredMorphs(acquiredMorphs);
        }
    }
}
