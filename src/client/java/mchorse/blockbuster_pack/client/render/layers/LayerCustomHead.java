package mchorse.blockbuster_pack.client.render.layers;

import com.mojang.authlib.GameProfile;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.ArmorSlot;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

import java.util.function.Function;

/**
 * Custom head feature layer (roadmap P76).
 *
 * <p>Port of Blockbuster 2.7.2's
 * {@code blockbuster_pack/client/render/layers/LayerCustomHead}. Renders the HEAD
 * equipment stack on every {@code slot: head} limb: player/mob skulls via the
 * vanilla skull renderer, other items (blocks, etc.) via the item renderer with
 * {@code ModelTransformationMode.HEAD}. Head-slot {@link ArmorItem} stacks are
 * skipped — {@link LayerActorArmor} draws those.</p>
 *
 * <p><b>Two exact transform paths</b> ({@link #applySkullTransform} /
 * {@link #applyItemTransform}) differ in the Y translate ({@code h/2} for skulls
 * vs {@code h/4} for items), the scale factor ({@code 1.1875} vs {@code 0.625})
 * and the item path's extra {@code rotate(180, Y)}. Both are golden-tested.</p>
 *
 * <p><b>Skull NBT write-back:</b> a string {@code SkullOwner} is upgraded to a
 * resolved {@link GameProfile} compound and <b>written back into the stack NBT</b>
 * (legacy cache behavior, preserved). Resolution is injected as a
 * {@link Function} so it is mockable/headless-testable with no network.</p>
 */
public class LayerCustomHead
{
    /**
     * Baked skull model, injected by the actor renderer (P80). Nullable so the
     * layer degrades to a no-draw (transform + profile resolution still run)
     * rather than crashing when the skull model is unavailable (total reader).
     */
    private final SkullBlockEntityModel skullModel;

    /** Profile resolver — vanilla path fills textures async; mocked in tests. */
    private final Function<GameProfile, GameProfile> profileResolver;

    public LayerCustomHead(SkullBlockEntityModel skullModel, Function<GameProfile, GameProfile> profileResolver)
    {
        this.skullModel = skullModel;
        this.profileResolver = profileResolver;
    }

    public void doRenderLayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ModelCustom model, float limbSwing, float scale)
    {
        ItemStack stack = entity.getEquippedStack(EquipmentSlot.HEAD);

        if (stack.isEmpty() || model.armor == null)
        {
            return;
        }

        for (ModelCustomRenderer limb : model.armor)
        {
            if (limb.limb.slot != ArmorSlot.HEAD)
            {
                continue;
            }

            matrices.push();
            limb.postRender(matrices, scale);

            this.renderItem(matrices, vertexConsumers, light, entity, stack, limb.limb, limbSwing);

            matrices.pop();
        }
    }

    protected void renderItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ItemStack stack, ModelLimb limb, float limbSwing)
    {
        Item item = stack.getItem();

        if (isSkull(item))
        {
            applySkullTransform(matrices, limb);

            GameProfile profile = this.resolveSkullProfile(stack);
            SkullBlock.SkullType type = ((AbstractSkullBlock) ((BlockItem) item).getBlock()).getSkullType();

            if (this.skullModel != null)
            {
                RenderLayer renderLayer = SkullBlockEntityRenderer.getRenderLayer(type, profile);

                SkullBlockEntityRenderer.renderSkull(Direction.UP, 180.0F, limbSwing, matrices, vertexConsumers, light, this.skullModel, renderLayer);
            }
        }
        else if (!(item instanceof ArmorItem) || ((ArmorItem) item).getSlotType() != EquipmentSlot.HEAD)
        {
            applyItemTransform(matrices, limb);

            MinecraftClient.getInstance().getItemRenderer().renderItem(entity, stack, ModelTransformationMode.HEAD, false, matrices, vertexConsumers, entity.getWorld(), light, OverlayTexture.DEFAULT_UV, entity.getId());
        }
    }

    /**
     * Skull transform: {@code translate(-w/4 + offsetX, h/2 - offsetY, d/4 -
     * offsetZ)}, {@code scale(1.1875 * w, -1.1875 * h, -1.1875 * d)} (w/h/d are
     * {@code size/8}). Note the {@code h/2} Y translate (not {@code h/4}).
     */
    public static void applySkullTransform(MatrixStack matrices, ModelLimb limb)
    {
        float w = limb.size[0] / 8F;
        float h = limb.size[1] / 8F;
        float d = limb.size[2] / 8F;

        float offsetX = limb.anchor[0] * w / 2;
        float offsetY = limb.anchor[1] * h / 2;
        float offsetZ = limb.anchor[2] * d / 2;

        matrices.translate(-w / 4 + offsetX, h / 2 - offsetY, d / 4 - offsetZ);
        matrices.scale(1.1875F * w, -1.1875F * h, -1.1875F * d);
    }

    /**
     * Item/block transform: {@code translate(-w/4 + offsetX, h/4 - offsetY, d/4 -
     * offsetZ)}, {@code rotate(180, Y)}, {@code scale(0.625 * w, -0.625 * h,
     * -0.625 * d)}.
     */
    public static void applyItemTransform(MatrixStack matrices, ModelLimb limb)
    {
        float w = limb.size[0] / 8F;
        float h = limb.size[1] / 8F;
        float d = limb.size[2] / 8F;

        float offsetX = limb.anchor[0] * w / 2;
        float offsetY = limb.anchor[1] * h / 2;
        float offsetZ = limb.anchor[2] * d / 2;

        matrices.translate(-w / 4 + offsetX, h / 4 - offsetY, d / 4 - offsetZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.scale(0.625F * w, -0.625F * h, -0.625F * d);
    }

    private static boolean isSkull(Item item)
    {
        return item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof AbstractSkullBlock;
    }

    /**
     * Resolve the skull's {@link GameProfile} from the stack NBT, upgrading a
     * string {@code SkullOwner} and <b>writing the resolved compound back</b> into
     * the stack (legacy behavior). Total: malformed/absent NBT → {@code null}.
     */
    public GameProfile resolveSkullProfile(ItemStack stack)
    {
        if (!stack.hasNbt())
        {
            return null;
        }

        /* Vanilla's own upgrade-and-write-back pass, which is what legacy's
         * synchronous TileEntitySkullRenderer.updateGameprofile became on
         * 1.20.1: it resolves a string SkullOwner off-thread and replaces it
         * with the completed compound in this very NBT. It is a no-op once the
         * key is already a compound, so it costs nothing after the first hit,
         * and running it first means the injected resolver below only sees the
         * profiles vanilla has not answered for yet. */
        fillSkullOwner(stack.getNbt());

        return resolveSkullProfile(stack.getNbt(), this.profileResolver);
    }

    /**
     * 1.20.1's vanilla skull-owner upgrade pass, inlined.
     *
     * <p>1.20.2+ exposes this as {@code SkullBlockEntity.fillSkullOwner(NbtCompound)};
     * on 1.20.1 the identical body sits in {@code SkullItem.postProcessNbt},
     * which cannot be called without an item instance. Same three steps in the
     * same order: skip unless {@code SkullOwner} is a non-blank string, hand a
     * name-only profile to {@link SkullBlockEntity#loadProperties} (async, and
     * cached by vanilla), and write the completed profile back over the string
     * when it lands.</p>
     */
    private static void fillSkullOwner(NbtCompound nbt)
    {
        if (!nbt.contains(SkullBlockEntity.SKULL_OWNER_KEY, NbtElement.STRING_TYPE))
        {
            return;
        }

        String name = nbt.getString(SkullBlockEntity.SKULL_OWNER_KEY);

        if (Util.isBlank(name))
        {
            return;
        }

        SkullBlockEntity.loadProperties(new GameProfile(null, name),
            profile -> nbt.put(SkullBlockEntity.SKULL_OWNER_KEY, NbtHelper.writeGameProfile(new NbtCompound(), profile)));
    }

    /**
     * The default profile resolver: vanilla's skull-owner lookup, reached
     * through the NBT-level API that is the only public entry point on 1.20.1.
     *
     * <p>Legacy's resolver returned the input profile unchanged whenever the
     * lookup missed, and the caller wrote <i>that</i> back — so an unresolvable
     * name pins itself as a textureless compound. Vanilla's async fill has the
     * same shape, so the behaviour carries over rather than being invented: a
     * miss leaves the name-only profile and a later hit replaces it in place.</p>
     */
    public static GameProfile resolveProfile(GameProfile profile)
    {
        if (profile == null || profile.getName() == null || profile.getName().isEmpty())
        {
            return profile;
        }

        NbtCompound nbt = new NbtCompound();

        nbt.putString(SkullBlockEntity.SKULL_OWNER_KEY, profile.getName());

        fillSkullOwner(nbt);

        GameProfile resolved = nbt.contains(SkullBlockEntity.SKULL_OWNER_KEY, NbtElement.COMPOUND_TYPE)
            ? NbtHelper.toGameProfile(nbt.getCompound(SkullBlockEntity.SKULL_OWNER_KEY))
            : null;

        return resolved == null ? profile : resolved;
    }

    /**
     * NBT-level skull owner resolution (extracted so it is headless-testable
     * without an {@link ItemStack}/item registry). Compound {@code SkullOwner} is
     * read directly; a string {@code SkullOwner} is resolved via {@code resolver}
     * and the resulting compound is <b>written back</b> into {@code nbt}. Total:
     * malformed/absent → {@code null}.
     */
    public static GameProfile resolveSkullProfile(NbtCompound nbt, Function<GameProfile, GameProfile> resolver)
    {
        if (nbt == null)
        {
            return null;
        }

        if (nbt.contains("SkullOwner", NbtElement.COMPOUND_TYPE))
        {
            return NbtHelper.toGameProfile(nbt.getCompound("SkullOwner"));
        }
        else if (nbt.contains("SkullOwner", NbtElement.STRING_TYPE))
        {
            String name = nbt.getString("SkullOwner");

            if (name != null && !name.isEmpty())
            {
                /* Legacy passed a null-UUID name-only profile; 1.20.4's authlib
                 * rejects null IDs, so use the placeholder NIL_UUID (matching
                 * vanilla name-only skull resolution). The resolver replaces it
                 * with the fetched profile. */
                GameProfile profile = resolver.apply(new GameProfile(Util.NIL_UUID, name));

                nbt.put("SkullOwner", NbtHelper.writeGameProfile(new NbtCompound(), profile));

                return profile;
            }
        }

        return null;
    }
}
