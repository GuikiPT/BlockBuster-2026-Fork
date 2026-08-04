package mchorse.blockbuster.common.item;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketPlaybackButton;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Playback button item (P98).
 *
 * <p>Push to start playing back a scene. Ported 1:1 from Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.common.item.ItemPlayback} — max stack 1, registry
 * name {@code playback}, tab set by {@link mchorse.blockbuster.common.BlockbusterTab}.</p>
 *
 * <p>Stack NBT keys (legacy contract): {@code Scene} (string filename),
 * {@code CameraProfile} (string) and {@code CameraPlay} (boolean). The tooltip
 * shows the profile line <i>before</i> the play line when both are present
 * (legacy precedence), and the scene line last.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/item/ItemPlayback.java}.</p>
 */
public class ItemPlayback extends Item
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "item.blockbuster.playback.name";
    }

    public ItemPlayback()
    {
        super(new Item.Settings().maxCount(1));
    }

    /**
     * Legacy {@code addInformation} — mirrors the key order and the
     * profile-before-play precedence exactly.
     */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        tooltip.add(Text.translatable("blockbuster.info.playback_button"));

        NbtCompound tag = stack.getNbt();

        if (tag == null)
        {
            return;
        }

        if (tag.contains("CameraProfile"))
        {
            tooltip.add(Text.translatable("blockbuster.info.playback_profile", tag.getString("CameraProfile")));
        }
        else if (tag.contains("CameraPlay"))
        {
            tooltip.add(Text.translatable("blockbuster.info.playback_play"));
        }

        if (tag.contains("Scene"))
        {
            tooltip.add(Text.translatable("blockbuster.info.playback_scene", tag.getString("Scene")));
        }
    }

    /**
     * Legacy {@code onItemRightClick} — the branch order is load-bearing and
     * preserved verbatim:
     *
     * <ol>
     *   <li>server + sneak + OP → open the playback config GUI (works even with
     *       a null tag — a fresh playback item can be configured immediately) →
     *       {@code SUCCESS};</li>
     *   <li>server + null tag → {@code PASS} (arm does not swing);</li>
     *   <li>server + non-empty {@code Scene} → toggle the scene (+ Aperture
     *       playback hand-off);</li>
     *   <li>otherwise → {@code SUCCESS} (empty-scene stacks swing but do
     *       nothing).</li>
     * </ol>
     *
     * <p>{@code EnumActionResult.SUCCESS} → {@link TypedActionResult#success},
     * {@code EnumActionResult.PASS} → {@link TypedActionResult#pass}.</p>
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand)
    {
        ItemStack stack = player.getStackInHand(hand);

        if (!world.isClient)
        {
            NbtCompound tag = stack.getNbt();

            if (player.isSneaking() && OpHelper.isPlayerOp((ServerPlayerEntity) player))
            {
                /* A null tag is treated as an empty one — a fresh item is
                 * configurable immediately (legacy behavior). */
                if (tag == null)
                {
                    tag = new NbtCompound();
                }

                String profile = tag.getString("CameraProfile");
                String scene = tag.getString("Scene");

                Dispatcher.sendTo(new PacketPlaybackButton(new SceneLocation(scene),
                    CameraHandler.getModeFromNBT(tag), profile)
                    .withScenes(CommonProxy.scenes.sceneFiles()), (ServerPlayerEntity) player);

                return TypedActionResult.success(stack);
            }

            if (tag == null)
            {
                return TypedActionResult.pass(stack);
            }

            String scene = tag.getString("Scene");

            /* Toggle the bound scene; if the toggle actually STARTED playback and
             * Aperture is loaded (always true — bundled), hand off to the camera. */
            if (!scene.isEmpty() && CommonProxy.scenes.toggle(scene, world) && CameraHandler.isApertureLoaded())
            {
                CameraHandler.handlePlaybackItem(player, tag);
            }
        }

        return TypedActionResult.success(stack);
    }
}
