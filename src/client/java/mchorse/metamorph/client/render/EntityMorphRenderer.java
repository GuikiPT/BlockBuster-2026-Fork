package mchorse.metamorph.client.render;

import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

/**
 * Client render body for {@link EntityMorph} and its subclasses (roadmap
 * P54/P80.2).
 *
 * <p>A vanilla-entity disguise renders "by proxy": the morph owns a dummy
 * {@code LivingEntity} (built in S4), the rendered entity's rotations are copied
 * onto it every frame so the disguise turns with its wearer, and vanilla's own
 * {@link EntityRenderDispatcher} draws it. Registered against
 * {@code EntityMorph} itself, so {@code UndeadMorph}/{@code ShulkerMorph}/
 * {@code IronGolemMorph} inherit it through the registry's superclass walk —
 * exactly as they inherited the render body by Java overriding on 1.12.2.</p>
 *
 * <p><b>Ported here:</b> the eight-field rotation copy (current <i>and</i> prev,
 * which is what keeps the disguise from juddering at low frame rates), the
 * uniform morph scale, the ender-dragon 180° flip, the sneak propagation, and
 * the GUI scale-normalization table.</p>
 *
 * <p>The {@code userTexture} override rides along on both paths through
 * {@link UserTextureSwap} — see there for why the in-world path has to flush
 * before it restores.</p>
 *
 * <p>{@code renderHand} landed with P54's tenth pass — the arm-limb search moved
 * to {@link EntityMorphArms}, since 1.20.4 has no flat {@code boxList} for its
 * third branch to sort.</p>
 *
 * <p><b>The body-part pass landed in P80.2</b> (S22 P227). Legacy's
 * {@code setupBodyPart}/{@code setupLimbs} pair became
 * {@link IEntityMorphBodyPartPass}: {@code arm} attaches
 * {@link LayerBodyPartFeature} to the mob's own renderer and points it at this
 * morph, the feature draws inside vanilla's model frame, {@code disarm} puts the
 * previous morph back. {@code setupLimbs}' field reflection is replaced by
 * {@link EntityMorphLimbParts} — see there and {@code EntityMorphLimbs} for why
 * the limb NAMES, not the fields, are the ported contract.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/morphs/EntityMorph.java (render/renderOnScreen)
 */
public class EntityMorphRenderer implements IMorphRenderer<EntityMorph>
{
    /**
     * The body-part pass (roadmap P80.2). Defaults to
     * {@link IEntityMorphBodyPartPass#NOOP} — a dedicated server and the headless
     * suite have no renderer to attach to — and is replaced in production by
     * {@code EntityMorphBodyPartPass.install()} from {@code MetamorphClient}.
     */
    public static IEntityMorphBodyPartPass bodyPartPass = IEntityMorphBodyPartPass.NOOP;

    /**
     * Legacy {@code EntityMorph.renderEntity} — the entity <i>wearing</i> the
     * disguise, published for the duration of the dummy's draw (roadmap P80.1).
     *
     * <p>Legacy put it on the morph class because its consumer was a Forge
     * event handler with no other way to reach the host; here it lives with the
     * render body, which is where the port put the rest of {@code
     * EntityMorph.render}. Two things read it: {@code
     * LivingEntityRendererNameMixin}, which suppresses the <b>dummy's</b> own
     * label while it is non-null (legacy's {@code event.setCanceled(true)}),
     * and {@link MorphNameplate}, which draws the host's name in its place.</p>
     *
     * <p>Cleared to {@code null} rather than restored, exactly as legacy did —
     * a nested entity-morph draw ends the outer one's label suppression early.
     * The quirk is reproduced, not tidied.</p>
     */
    public static LivingEntity renderEntity;
    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(EntityMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        if (entity == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return;
        }

        LivingEntity dummy = morph.getEntity(entity.getWorld());

        if (dummy == null)
        {
            return;
        }

        copyRotations(entity, dummy);

        /* 1.20.4 pulls the model's sneak flag from entity pose state rather than
         * a ModelBiped field, so the legacy isSneak save/restore becomes a pose
         * swap on the dummy. */
        EntityPose pose = dummy.getPose();

        dummy.setPose(entity.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);

        MatrixStack matrices = context.matrices;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        UserTextureSwap swap = new UserTextureSwap();

        /* P80.2 — legacy setupBodyPart(): arm the mob renderer's body-part
         * layer for the duration of this draw. */
        Object armed = bodyPartPass.arm(dispatcher, dummy, morph);

        /* Legacy `renderEntity = entity;` right before the dummy is drawn: it
         * both cancels the dummy's own label and names the host whose display
         * name replaces it. */
        renderEntity = entity;

        matrices.push();

        try
        {
            matrices.translate(x, y, z);
            matrices.scale(morph.scale, morph.scale, morph.scale);

            if (dummy instanceof EnderDragonEntity)
            {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
            }

            swap.replace(morph.userTexture, mobTexture(dispatcher, dummy));

            dispatcher.setRenderShadows(false);
            dispatcher.render(dummy, 0, 0, 0, entityYaw, partialTicks, matrices, context.consumers, context.light);
            dispatcher.setRenderShadows(true);

            /* Legacy's onNameRender: the host's name in place of the dummy's,
             * drawn inside the same translate + scale frame the dummy got. */
            MorphNameplate.draw(entity, dummy, matrices, context.consumers, context.light);

            if (swap.isSwapped())
            {
                UserTextureSwap.flush(context.consumers);
            }
        }
        finally
        {
            /* Legacy `renderEntity = null;` — see the field's note on why this
             * is a clear rather than a restore. */
            renderEntity = null;

            bodyPartPass.disarm(armed);
            swap.restore();
            matrices.pop();
            dummy.setPose(pose);
        }
    }

    /**
     * Legacy's {@code setupTexture()}: the texture the entity's own renderer
     * would use, which is the id the {@code userTexture} swap has to displace.
     * Resolved per draw rather than cached, since 1.20.4 renderers pick their
     * texture from live entity state (a sheared sheep, a charged creeper).
     */
    public static Identifier mobTexture(EntityRenderDispatcher dispatcher, LivingEntity dummy)
    {
        if (dispatcher == null || dummy == null)
        {
            return null;
        }

        @SuppressWarnings("unchecked")
        EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) dispatcher.getRenderer(dummy);

        return renderer == null ? null : renderer.getTexture(dummy);
    }

    /**
     * Legacy's "make transformation seamless" block: both the current and the
     * previous value of all four rotations, so the dummy interpolates over the
     * same arc the wearer does.
     */
    public static void copyRotations(LivingEntity from, LivingEntity to)
    {
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        to.headYaw = from.headYaw;
        to.bodyYaw = from.bodyYaw;

        to.prevYaw = from.prevYaw;
        to.prevPitch = from.prevPitch;
        to.prevHeadYaw = from.prevHeadYaw;
        to.prevBodyYaw = from.prevBodyYaw;
    }

    /* --------------------------------------------------------------------- */
    /* First-person arm                                                      */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code EntityMorph.renderHand}: draw one of the disguise's two
     * "hand" limbs (found by {@link EntityMorphArms}) where the player's arm
     * would be.
     *
     * <p><b>It returns true on every path.</b> Hand-less settings, no dummy, no
     * arms found, a broken model — all claim the hand and draw nothing, which is
     * the legacy behaviour: a mob disguise never shows the player's own arm.</p>
     *
     * <p><b>The two branches are not symmetric, and that is legacy's.</b> The
     * main hand zeroes all three of the limb's rotation angles; the off hand
     * zeroes only {@code rotateAngleX} and leaves Y and Z as the pose left them.
     * Both pin the pivot to {@code (∓6, 4, 0)}. Reproduced, not tidied.</p>
     *
     * <p>Legacy's {@code triedHands} latch cached the search on the morph; here
     * the search runs per draw. It is a handful of field reads on a biped and a
     * short tree walk otherwise, and the model instance behind a morph can
     * change on a resource reload — a latch would pin a stale {@code ModelPart}
     * that no longer belongs to any model.</p>
     */
    @Override
    public boolean renderHand(EntityMorph morph, PlayerEntity player, Hand hand)
    {
        if (morph.claimsHandByDefault())
        {
            return true;
        }

        MorphRenderContext context = MorphRenderContext.current();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (context == null || context.matrices == null || context.consumers == null || mc == null || player == null)
        {
            return true;
        }

        LivingEntity dummy = morph.getEntity(player.getWorld());

        if (dummy == null)
        {
            return true;
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityModel<?> model = modelOf(dispatcher, dummy);
        Identifier texture = mobTexture(dispatcher, dummy);

        if (model == null || texture == null)
        {
            return true;
        }

        EntityMorphArms.Arms arms = EntityMorphArms.find(model);

        if (!arms.complete())
        {
            return true;
        }

        UserTextureSwap swap = new UserTextureSwap();

        try
        {
            swap.replace(morph.userTexture, texture);

            /* Legacy: model.swingProgress = 0; setRotationAngles(0,…,0.0625, entity)
             * — the neutral, stationary pose, so the arm is not mid-stride. */
            model.handSwingProgress = 0F;

            setNeutralAngles(model, dummy);

            ModelPart arm = hand == Hand.MAIN_HAND ? arms.right : arms.left;
            boolean mainHand = hand == Hand.MAIN_HAND;

            float pitch = arm.pitch;
            float yaw = arm.yaw;
            float roll = arm.roll;
            float pivotX = arm.pivotX;
            float pivotY = arm.pivotY;
            float pivotZ = arm.pivotZ;

            arm.pitch = 0F;

            if (mainHand)
            {
                arm.yaw = 0F;
                arm.roll = 0F;
            }

            arm.pivotX = mainHand ? -6F : 6F;
            arm.pivotY = 4F;
            arm.pivotZ = 0F;

            context.matrices.push();

            try
            {
                /* Legacy's ModelRenderer.render(scale) applied the 1/16 factor
                 * itself; on 1.20.4 ModelPart.render works in the caller's units,
                 * so the factor is an explicit scale around the draw. */
                context.matrices.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);

                /* Always the mob's own id: UserTextureSwap works by putting the
                 * user texture's object under that id, so the layer must ask for
                 * the id the swap displaced, not for the override. */
                arm.render(context.matrices,
                    context.consumers.getBuffer(RenderLayer.getEntityTranslucent(texture)),
                    context.light, context.overlay);
            }
            finally
            {
                context.matrices.pop();
            }

            arm.pitch = pitch;
            arm.yaw = yaw;
            arm.roll = roll;
            arm.pivotX = pivotX;
            arm.pivotY = pivotY;
            arm.pivotZ = pivotZ;

            if (swap.isSwapped())
            {
                UserTextureSwap.flush(context.consumers);
            }
        }
        finally
        {
            swap.restore();
        }

        return true;
    }

    /** Legacy passed {@code 0.0625F} to {@code ModelRenderer.render}. */
    public static final float ARM_SCALE = 0.0625F;

    /**
     * The model behind a dummy's renderer — {@code renderer.getMainModel()} on
     * 1.12.2. Null for any renderer that is not a {@link LivingEntityRenderer}
     * (an item-frame-shaped or otherwise model-less entity), which the caller
     * reads as "no arm".
     */
    public static EntityModel<?> modelOf(EntityRenderDispatcher dispatcher, LivingEntity dummy)
    {
        if (dispatcher == null || dummy == null)
        {
            return null;
        }

        EntityRenderer<?> renderer = dispatcher.getRenderer(dummy);

        return renderer instanceof LivingEntityRenderer
            ? ((LivingEntityRenderer<?, ?>) renderer).getModel()
            : null;
    }

    /**
     * Legacy's {@code setRotationAngles(0, 0, 0, 0, 0, 0.0625F, entity)}. The
     * generic bound is unenforceable here — the dummy is whatever entity the
     * morph wraps and the model is that entity's — so the cast is unchecked by
     * construction, exactly as legacy's {@code @SuppressWarnings("rawtypes")}
     * was.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setNeutralAngles(EntityModel<?> model, LivingEntity dummy)
    {
        ((EntityModel) model).setAngles(dummy, 0F, 0F, 0F, 0F, 0F);
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    @Override
    public void renderOnScreen(EntityMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        if (player == null)
        {
            return;
        }

        LivingEntity dummy = morph.getEntity(player.getWorld());

        if (dummy == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        UserTextureSwap swap = new UserTextureSwap();

        /* P80.2 — legacy renderOnScreen armed the same layer before
         * drawEntityOnScreen, which renders through the same dispatcher. */
        Object armed = mc == null ? null : bodyPartPass.arm(mc.getEntityRenderDispatcher(), dummy, morph);

        try
        {
            swap.replace(morph.userTexture, mc == null ? null : mobTexture(mc.getEntityRenderDispatcher(), dummy));

            GuiUtils.drawEntityOnScreen(x, y, guiScale(scale, dummy.getHeight(), morph.name), dummy, alpha);
        }
        finally
        {
            bodyPartPass.disarm(armed);

            /* No flush here: drawEntityOnScreen ends with DrawContext.draw(). */
            swap.restore();
        }
    }

    /**
     * The GUI scale-normalization table, verbatim from legacy
     * {@code EntityMorph.renderOnScreen}. Pure so the whole matrix of
     * heights × special-cased names is headless-testable.
     *
     * <ul>
     *   <li>taller than 2 → shrink to fit 2 blocks;</li>
     *   <li>shorter than 0.6 → grow to a half-block "minimum";</li>
     *   <li>ghast → a flat {@code 5} (<b>replaces</b> the scale, it does not
     *       multiply it);</li>
     *   <li>guardian taller than 1.8 → a second {@code 1/height} shrink on top
     *       of the {@code >2} one.</li>
     * </ul>
     */
    public static float guiScale(float scale, float height, String name)
    {
        if (height > 2)
        {
            scale *= 2 / height;
        }
        else if (height < 0.6)
        {
            scale *= 0.5 / height;
        }

        if ("minecraft:ghast".equals(name))
        {
            scale = 5F;
        }
        else if ("minecraft:guardian".equals(name) && height > 1.8)
        {
            scale *= 1 / height;
        }

        return scale;
    }
}
