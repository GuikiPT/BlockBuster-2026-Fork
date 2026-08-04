package mchorse.metamorph.api.morphs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;

/**
 * Legacy limb-name → yarn model-part-name translation table (roadmap P53/P227).
 *
 * <p><b>The limb names are a body-part contract.</b> In 1.12.2 Blockbuster,
 * {@link mchorse.metamorph.api.morphs.EntityMorph} built its limb map by
 * reflecting over the {@code ModelBase}'s {@code ModelRenderer} <i>fields</i>,
 * so a stored {@code BodyPart.limb} string is a 1.12 field name
 * ({@code bipedHead}, {@code bipedRightArm}, {@code leg1}, …). On 1.20.4 the
 * limb map is instead built from the {@code ModelPart} child tree whose keys are
 * the yarn part names ({@code head}, {@code right_arm}, {@code right_hind_leg},
 * …). This table lets an old {@code limb} string keep attaching to the same
 * physical limb after the port.</p>
 *
 * <p>Total-reader rule: {@link #translate(String)} returns the mapped yarn name
 * when known, otherwise returns the input unchanged — a limb string that is
 * already a yarn part name, or a custom-model part name, passes through, and the
 * client renderer simply skips it if no such part exists on the model. A name
 * that <i>looks</i> like a 1.12 {@code ModelBase} field (it carries an uppercase
 * letter, which no yarn part name does) but has no entry gets a one-shot warning
 * on the way through, the P71 shim's discipline — never a crash, never a silent
 * drop.</p>
 *
 * <p>Only the two vanilla base models Metamorph's reflection could ever populate
 * are mapped here (biped and quadruped); the field/part correspondence follows
 * 1.12 {@code ModelBiped}/{@code ModelQuadruped} and yarn
 * {@code BipedEntityModel}/{@code QuadrupedEntityModel} + {@code
 * EntityModelPartNames}.</p>
 *
 * <h2>Where the translation runs (P227)</h2>
 *
 * <p>{@link #translate(BodyPartManager)} is called from
 * {@link EntityMorph#fromNBT(net.minecraft.nbt.NbtCompound)} — <b>on load, once,
 * not per frame</b> — and <b>only for {@link EntityMorph}</b>. That scoping is
 * load-bearing: {@code CustomMorph} is the other {@code IBodyPartProvider}, and
 * its parts name limbs of a Blockbuster {@code model.json}, which are free-form
 * author strings. The captured 2.7.2 fixtures in this repo contain body parts on
 * {@code blockbuster.eyes/fred} naming {@code head}/{@code left_arm}/{@code
 * right_arm} and on {@code blockbuster.Crafting Table} naming {@code cube} — a
 * blanket translation would corrupt any custom model whose author happened to
 * name a limb {@code leg1}.</p>
 *
 * <p>The raw legacy string is kept on the part ({@code BodyPart.loadedLimb}) and
 * re-emitted by {@code toNBT}, the same byte-parity contract {@code loadedSlots}
 * gives the six item slots: an old file that this client loads and saves keeps
 * its {@code bipedHead}, so it still opens in 1.12.2.</p>
 */
public final class EntityMorphLimbs
{
    /** Legacy 1.12 {@code ModelBiped}/{@code ModelQuadruped} field → yarn part. */
    public static final Map<String, String> TABLE;

    static
    {
        Map<String, String> map = new HashMap<String, String>();

        /* ModelBiped fields */
        map.put("bipedHead", "head");
        map.put("bipedHeadwear", "hat");
        map.put("bipedBody", "body");
        map.put("bipedRightArm", "right_arm");
        map.put("bipedLeftArm", "left_arm");
        map.put("bipedRightLeg", "right_leg");
        map.put("bipedLeftLeg", "left_leg");

        /* ModelQuadruped fields (leg1=back-right, leg2=back-left,
         * leg3=front-right, leg4=front-left in 1.12) */
        map.put("head", "head");
        map.put("body", "body");
        map.put("leg1", "right_hind_leg");
        map.put("leg2", "left_hind_leg");
        map.put("leg3", "right_front_leg");
        map.put("leg4", "left_front_leg");

        TABLE = Collections.unmodifiableMap(map);
    }

    /** One-shot warning keys, so a replayed record cannot spam the log. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private EntityMorphLimbs()
    {}

    /**
     * Translate a legacy limb name to the yarn model-part name, or pass the
     * input through unchanged when it isn't a known legacy field.
     */
    public static String translate(String limb)
    {
        if (limb == null)
        {
            return null;
        }

        String mapped = TABLE.get(limb);

        if (mapped == null)
        {
            if (looksLegacy(limb) && WARNED.add(limb))
            {
                Metamorph.LOGGER.warn("P227 legacy body-part limb \"{}\" has no 1.20.4 model-part mapping — keeping it verbatim; the part will not attach unless the model has a part by that name", limb);
            }

            return limb;
        }

        return mapped;
    }

    /**
     * Whether a limb string is shaped like a 1.12 {@code ModelBase} field rather
     * than a yarn model-part name. Every yarn part key is lower snake_case
     * (see {@code EntityModelPartNames}), so a single uppercase letter is a
     * reliable tell for {@code bipedRightArm}-style input. Deliberately not a
     * whitelist: modded 1.12 models named their fields whatever they liked, and
     * this only decides whether to log.
     */
    public static boolean looksLegacy(String limb)
    {
        for (int i = 0; i < limb.length(); i++)
        {
            if (Character.isUpperCase(limb.charAt(i)))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Translate every part of an {@link EntityMorph}'s body-part manager in
     * place, remembering each part's raw legacy string for byte-parity re-emit.
     * Null-tolerant: a morph with no parts is a no-op.
     *
     * @return how many parts were actually rewritten (0 when the file was
     *         already modern) — the count the P227 tests assert on.
     */
    public static int translate(BodyPartManager parts)
    {
        if (parts == null || parts.parts == null)
        {
            return 0;
        }

        int translated = 0;

        for (BodyPart part : parts.parts)
        {
            if (part != null && part.translateLegacyLimb())
            {
                translated += 1;
            }
        }

        return translated;
    }
}
