package mchorse.metamorph.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import mchorse.mclib.utils.NBTUtils;
import mchorse.metamorph.api.events.RegisterBlacklistEvent;
import mchorse.metamorph.api.events.RegisterRemapEvent;
import mchorse.metamorph.api.events.RegisterSettingsEvent;
import mchorse.metamorph.api.models.IMorphProvider;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Morph static toolbox (roadmap P48).
 *
 * <p>Data/NBT/buffer helpers plus the {@code anyMatch} traversal and
 * pause/resume dispatch. {@code MorphUtils.pause} checks {@link ISyncableMorph}
 * <b>before</b> {@link IBodyPartProvider}; {@code anyMatch} only recurses into
 * body parts whose {@code enabled} is true and follows {@link IMorphProvider}
 * chains in a {@code while(true)} loop.</p>
 *
 * <p>Port note: the client render trap ({@code render}/{@code renderDirect}/
 * {@code renderOnScreen}, the shadow-pass filter, the leaked-tessellator
 * cleanup) is P54 client-render surface and lives in the client source set;
 * it is not part of this data core. {@code morphToBuf}/{@code morphFromBuf}
 * use {@link PacketByteBuf} (the S2 chunked/infinite NBT read via
 * {@code NBTUtils.readInfiniteTag} bypasses the vanilla 2 MiB tag cap).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/MorphUtils.java
 */
public class MorphUtils
{
    /**
     * Generate an empty file
     */
    public static void generateFile(File config, String content)
    {
        config.getParentFile().mkdirs();

        try
        {
            PrintWriter writer = new PrintWriter(config);
            writer.print(content);
            writer.close();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Reload blacklist using event
     */
    public static Set<String> reloadBlacklist()
    {
        RegisterBlacklistEvent event = new RegisterBlacklistEvent();
        MetamorphEvents.REGISTER_BLACKLIST.invoker().accept(event);

        return event.blacklist;
    }

    /**
     * Reload morph settings using event
     */
    public static Map<String, MorphSettings> reloadMorphSettings()
    {
        RegisterSettingsEvent event = new RegisterSettingsEvent();
        MetamorphEvents.REGISTER_SETTINGS.invoker().accept(event);

        return event.settings;
    }

    /**
     * Reload morph ID mappings using event
     */
    public static Map<String, String> reloadRemapper()
    {
        RegisterRemapEvent event = new RegisterRemapEvent();
        MetamorphEvents.REGISTER_REMAP.invoker().accept(event);

        return event.map;
    }

    /**
     * Copy a morph
     */
    public static AbstractMorph copy(AbstractMorph morph)
    {
        return morph == null ? null : morph.copy();
    }

    /**
     * Pause given morph
     */
    public static boolean pause(AbstractMorph morph, AbstractMorph previous, int offset)
    {
        if (morph instanceof ISyncableMorph)
        {
            ((ISyncableMorph) morph).pause(previous, offset);

            return true;
        }
        else if (morph instanceof IBodyPartProvider)
        {
            ((IBodyPartProvider) morph).getBodyPart().pause(previous, offset);

            return true;
        }

        return false;
    }

    /**
     * Resume given morph from pause.
     */
    public static boolean resume(AbstractMorph morph)
    {
        if (morph instanceof ISyncableMorph)
        {
            ((ISyncableMorph) morph).resume();

            return true;
        }
        else if (morph instanceof IBodyPartProvider)
        {
            for (BodyPart part : ((IBodyPartProvider) morph).getBodyPart().parts)
            {
                if (!part.morph.isEmpty() && part.morph.get() instanceof ISyncableMorph)
                {
                    ((ISyncableMorph) part.morph.get()).resume();
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Morph to NBT
     */
    public static NbtCompound toNBT(AbstractMorph morph)
    {
        if (morph == null)
        {
            return null;
        }

        return morph.toNBT();
    }

    /**
     * Write a morph to a {@link PacketByteBuf} (null-safe).
     *
     * <p>Use in conjunction with {@link #morphFromBuf(PacketByteBuf)}.</p>
     */
    public static void morphToBuf(PacketByteBuf buffer, AbstractMorph morph)
    {
        buffer.writeNbt(morph == null ? null : morph.toNBT());
    }

    /**
     * Create a morph from a {@link PacketByteBuf}. Reads with the unlimited
     * tag-size tracker so oversized Blockbuster morphs (sequencers full of
     * custom models) can exceed the vanilla 2 MiB cap.
     *
     * <p>Use in conjunction with {@link #morphToBuf(PacketByteBuf, AbstractMorph)}.</p>
     */
    public static AbstractMorph morphFromBuf(PacketByteBuf buffer)
    {
        return MorphManager.INSTANCE.morphFromNBT(NBTUtils.readInfiniteTag(buffer));
    }

    /**
     * Traverses the morph, its <b>enabled</b> body parts (recursively), and
     * its {@link IMorphProvider} chain to find a morph matching the predicate.
     */
    public static boolean anyMatch(AbstractMorph morph, Predicate<AbstractMorph> condition)
    {
        while (true)
        {
            if (condition.test(morph))
            {
                return true;
            }

            if (morph instanceof IBodyPartProvider)
            {
                BodyPartManager mgr = ((IBodyPartProvider) morph).getBodyPart();
                for (BodyPart part : mgr.parts)
                {
                    if (part.enabled)
                    {
                        if (anyMatch(part.morph.get(), condition))
                        {
                            return true;
                        }
                    }
                }
            }

            if (morph instanceof IMorphProvider)
            {
                morph = ((IMorphProvider) morph).getMorph();
            }
            else
            {
                break;
            }
        }

        return false;
    }
}
