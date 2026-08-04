package mchorse.blockbuster.client.render;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.render.layer.LayerHeldItem;
import mchorse.blockbuster_pack.client.render.layers.LayerActorArmor;
import mchorse.blockbuster_pack.client.render.layers.LayerCustomHead;
import mchorse.blockbuster_pack.client.render.layers.LayerElytra;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Actor custom-model renderer specialisation (roadmap P80).
 *
 * <p>Port of Blockbuster 2.7.2's {@code blockbuster_pack/client/render/
 * RenderCustomActor}, which extended {@code RenderCustomModel} and added three
 * things on top of the base custom-model renderer:</p>
 *
 * <ol>
 *   <li>the fixed <b>five-layer stack, in order</b> — a visual contract
 *       (elytra under body parts under armor under head/held-item), captured in
 *       {@link #LAYER_ORDER} and run by {@link #renderLayers};</li>
 *   <li>a skin override: {@code getEntityTexture} returns the morph's
 *       {@code CustomMorph.skin} when set, else the model default (done at the
 *       call site here — {@code CustomMorphRenderer} resolves the texture before
 *       it picks a {@code RenderLayer}, since 1.20.4 binds by layer rather than
 *       by a renderer callback);</li>
 *   <li>a looser nametag rule than the base renderer: it also shows the name
 *       when the {@code actor_always_render_names} config is on
 *       ({@link #canRenderName}).</li>
 * </ol>
 *
 * <p><b>Why the stack lives here and not on the base class.</b> 1.12.2 registered
 * no layers on {@code RenderCustomModel} at all; every layer was added by this
 * subclass's constructor. That is not an accident of organisation — it is what
 * makes a bare custom-model draw (a model block, a GUI preview) render the model
 * and nothing else, while a morph on a living entity picks up its armor, elytra,
 * hat and held items. The split is preserved exactly.</p>
 *
 * <p><b>Lazy models.</b> Three of the four layers need baked vanilla model parts
 * ({@code PLAYER_OUTER_ARMOR}, {@code PLAYER_INNER_ARMOR}, {@code PLAYER_HEAD}),
 * which only exist once the client's {@link EntityModelLoader} has reloaded. The
 * shared renderer instance is constructed at class-init time — long before that,
 * and in headless tests never — so the stack is built on first use and stays
 * null when there is no client. A null stack simply skips those layers; the
 * body-part layer, which needs nothing baked, still runs.</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/client/render/RenderCustomActor.java
 */
public class RenderCustomActor extends RenderCustomModel
{
    /**
     * The feature-layer registration order — a visual contract. Legacy
     * {@code RenderCustomActor} added the layers in exactly this sequence; the
     * order matters because armor draws over body parts, and head/held-item
     * draw over armor.
     */
    public static final String[] LAYER_ORDER =
    {
        "LayerElytra",
        "LayerBodyPart",
        "LayerActorArmor",
        "LayerCustomHead",
        "LayerHeldItem"
    };

    /** Built on first render, or left null when there is no client (headless). */
    private Layers layers;

    /**
     * Legacy {@code RenderCustomActor.canRenderName}:
     * {@code hasCustomName() && (actorAlwaysRenderNames || (guiEnabled &&
     * entity == pointedEntity))}. Note this does <b>not</b> chain the vanilla
     * super check the way {@link RenderCustomModel#canRenderName} does — an
     * actor with a custom name shows it whenever the always-render-names config
     * is on, regardless of distance or line-of-sight.
     *
     * @param hasCustomName   the actor has a custom name
     * @param guiEnabled      the game GUI is shown (F1 hides nametags)
     * @param isPointedEntity the crosshair is on this actor
     */
    public static boolean canRenderName(boolean hasCustomName, boolean guiEnabled, boolean isPointedEntity)
    {
        return hasCustomName && (Blockbuster.actorAlwaysRenderNames.get() || (guiEnabled && isPointedEntity));
    }

    /**
     * The override legacy declared: {@code RenderCustomActor.canRenderName}
     * replaces the base rule outright and <b>drops {@code vanillaRule}</b> — an
     * actor with a custom name shows it whenever {@code actor_always_render_names}
     * is on, regardless of distance, line of sight, team visibility or F1.
     *
     * <p>This is the method {@code RenderCustomModel.renderName} calls, and
     * {@link mchorse.blockbuster_pack.client.render.CustomMorphRenderer#RENDERER}
     * is a {@code RenderCustomActor} — exactly as {@code ClientProxy.actorRenderer}
     * was — so this override, not the base rule, is what a morph draw uses
     * (S22, batch V-J: before that the config key was inert).</p>
     */
    @Override
    protected boolean canRenderName(boolean vanillaRule, boolean hasCustomName, boolean hudEnabled, boolean isPointedEntity)
    {
        return canRenderName(hasCustomName, hudEnabled, isPointedEntity);
    }

    /**
     * The five-layer pass, in legacy registration order. The body-part layer sits
     * second, which is {@code super.renderLayers} — and running it through super
     * is also what keeps the shared-renderer restore ({@code current} /
     * {@code poseContext} / {@code setupModel}) <i>between</i> the body parts and
     * the three layers that follow them, exactly where legacy's
     * {@code LayerBodyPart} tail put it. Without that, a body part holding a
     * nested {@code CustomMorph} would leave armor, hat and held items reading
     * the child's model.
     *
     * <p>Every layer draws through the frame's {@link VertexConsumerProvider},
     * which the base render call does not receive (it takes the single
     * {@code VertexConsumer} the model geometry goes to), so it comes off the
     * {@link MorphRenderContext} stack — the same source {@code LayerBodyPart}
     * uses. No frame, no layers.</p>
     */
    @Override
    protected void renderLayers(LivingEntity entity, float partialTicks, MatrixStack matrices)
    {
        ModelCustom model = this.mainModel;
        PoseContext pose = this.poseContext;
        MorphRenderContext frame = MorphRenderContext.current();
        Layers layers = this.layers();

        boolean drawable = entity != null && model != null && layers != null
            && frame != null && frame.consumers != null && matrices != null;

        if (drawable)
        {
            layers.elytra.doRenderLayer(matrices, frame.consumers, frame.light, entity, model,
                BODY_PART_SCALE, entity.isFallFlying(), entity.isInSneakingPose());
        }

        super.renderLayers(entity, partialTicks, matrices);

        if (!drawable)
        {
            return;
        }

        /* super restored the renderer's fields, so re-read the model it points
         * at rather than trusting the local from before the body parts. */
        model = this.mainModel;

        if (model == null)
        {
            return;
        }

        float limbSwing = pose == null ? 0F : pose.limbSwing;
        float limbSwingAmount = pose == null ? 0F : pose.limbSwingAmount;
        float ageInTicks = pose == null ? 0F : pose.ageInTicks;
        float netHeadYaw = pose == null ? 0F : pose.netHeadYaw;
        float headPitch = pose == null ? 0F : pose.headPitch;

        layers.armor.doRenderLayer(matrices, frame.consumers, frame.light, entity, model, BODY_PART_SCALE);
        layers.head.doRenderLayer(matrices, frame.consumers, frame.light, entity, model, limbSwing, BODY_PART_SCALE);
        layers.heldItem.doRenderLayer(matrices, frame.consumers, frame.light, entity, model,
            limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, BODY_PART_SCALE);
    }

    /**
     * Build the stack once the baked model parts exist. Returns null while there
     * is no client or the model loader has not reloaded yet — the layers are then
     * skipped for that frame and retried on the next one, rather than caching a
     * half-built stack.
     */
    private Layers layers()
    {
        if (this.layers != null)
        {
            return this.layers;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getEntityModelLoader() == null)
        {
            return null;
        }

        try
        {
            EntityModelLoader loader = mc.getEntityModelLoader();

            this.layers = new Layers(
                new LayerElytra(),
                new LayerActorArmor(
                    new BipedEntityModel<LivingEntity>(loader.getModelPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                    new BipedEntityModel<LivingEntity>(loader.getModelPart(EntityModelLayers.PLAYER_INNER_ARMOR))),
                new LayerCustomHead(
                    new SkullEntityModel(loader.getModelPart(EntityModelLayers.PLAYER_HEAD)),
                    LayerCustomHead::resolveProfile),
                new LayerHeldItem());
        }
        catch (Exception e)
        {
            /* getModelPart throws when the layer has not been baked yet. Not an
             * error worth logging every frame — the next frame retries. */
            return null;
        }

        return this.layers;
    }

    /** The four instance layers, built together so the stack is all-or-nothing. */
    private static final class Layers
    {
        final LayerElytra elytra;
        final LayerActorArmor armor;
        final LayerCustomHead head;
        final LayerHeldItem heldItem;

        Layers(LayerElytra elytra, LayerActorArmor armor, LayerCustomHead head, LayerHeldItem heldItem)
        {
            this.elytra = elytra;
            this.armor = armor;
            this.head = head;
            this.heldItem = heldItem;
        }
    }
}
