package mchorse.metamorph.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Morph class → client renderer registry, and the {@link
 * AbstractMorph.IRenderDispatcher} implementation that routes the two morph
 * render methods into it (roadmap P54).
 *
 * <p><b>Superclass walk.</b> Legacy got subclass rendering for free through
 * Java overriding: {@code UndeadMorph extends EntityMorph} inherited
 * {@code EntityMorph}'s render body unless it declared its own. A flat class →
 * renderer map would lose that, so lookup walks up the superclass chain until a
 * registration is found, and the resolution is memoised per concrete class
 * (including the "no renderer" answer, so an unregistered morph type costs one
 * walk, not one per frame).</p>
 *
 * <p><b>Totality.</b> An unregistered morph draws nothing rather than throwing
 * — the same observable behaviour as before any renderer existed. Renderers
 * themselves are invoked through {@code MorphRenderUtils}, which owns the
 * {@code errorRendering} latch; this class deliberately adds no second trap.</p>
 */
public final class MorphRendererRegistry
{
    /** Explicit registrations, keyed by the exact morph class. */
    private static final Map<Class<? extends AbstractMorph>, IMorphRenderer<?>> RENDERERS =
        new HashMap<Class<? extends AbstractMorph>, IMorphRenderer<?>>();

    /** Resolved (walked) answers per concrete morph class; may hold nulls. */
    private static final Map<Class<?>, IMorphRenderer<?>> RESOLVED = new HashMap<Class<?>, IMorphRenderer<?>>();


    private MorphRendererRegistry()
    {}

    /**
     * {@code ? super T} because a renderer written against a base morph class
     * is a perfectly good renderer for any subclass — which is how the
     * inheritance the superclass walk restores is expressed in the type system.
     */
    public static <T extends AbstractMorph> void register(Class<T> morphClass, IMorphRenderer<? super T> renderer)
    {
        RENDERERS.put(morphClass, renderer);

        /* A later registration for a superclass must invalidate answers that
         * were walked past it. */
        RESOLVED.clear();
    }

    /**
     * The renderer for a morph's class, or the nearest registered superclass's,
     * or null.
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractMorph> IMorphRenderer<T> get(T morph)
    {
        if (morph == null)
        {
            return null;
        }

        Class<?> key = morph.getClass();

        if (RESOLVED.containsKey(key))
        {
            return (IMorphRenderer<T>) RESOLVED.get(key);
        }

        IMorphRenderer<?> renderer = null;

        for (Class<?> c = key; c != null && AbstractMorph.class.isAssignableFrom(c); c = c.getSuperclass())
        {
            renderer = RENDERERS.get(c);

            if (renderer != null)
            {
                break;
            }
        }

        RESOLVED.put(key, renderer);

        return (IMorphRenderer<T>) renderer;
    }

    /** Test/reload hook: forget every registration. */
    public static void clear()
    {
        RENDERERS.clear();
        RESOLVED.clear();
    }

    /**
     * Install this registry as {@link AbstractMorph#renderDispatcher}. Called
     * once at client init, after the renderers are registered.
     */
    public static void install()
    {
        AbstractMorph.renderDispatcher = new Dispatcher();
    }

    /** Routes {@code AbstractMorph}'s two render methods into the registry. */
    public static class Dispatcher implements AbstractMorph.IRenderDispatcher
    {
        @Override
        public void render(AbstractMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
        {
            IMorphRenderer<AbstractMorph> renderer = get(morph);
            MorphRenderContext context = MorphRenderContext.current();

            if (renderer != null && context != null)
            {
                renderer.render(morph, entity, x, y, z, entityYaw, partialTicks, context);
            }
        }

        /**
         * <b>Lighting.</b> {@code GameRenderer.render} arms
         * {@code DiffuseLighting.enableGuiDepthLighting()} before any GUI draw.
         * That is the <i>side-lit</i> preset, rotated to suit the 30°/225°
         * {@code gui} transform an item icon carries, and it is wrong for these
         * previews — they are near-front-on views, so it leaves a face on bare
         * ambient (0.40) on every chain. The flat preset is vanilla's
         * front-facing one (what a {@code gui_light: front} model gets), and it
         * is the only one that lights all three preview chains at once:
         *
         * <pre>
         *                        side    top     face/picture
         *   Blockbuster model    0.40    0.77    1.00
         *   Chameleon            1.00    0.73    0.87
         *   image morph           -       -      1.00
         * </pre>
         *
         * <p>One preset is needed because the chains disagree about normals and
         * no single light <i>vector</i> can suit them: {@code CustomMorphRenderer
         * .drawModel} puts its {@code S(-1,-1,1)} through
         * {@code multiplyPositionMatrix}, so its lighting normals are the X/Y
         * negation of its visible ones, while {@code ChameleonMorphRenderer}
         * scales uniformly and positive and its two agree. A pair that lights
         * one chain's top face darkens the other's — the flat preset spreads its
         * two lights widely enough to cover both.</p>
         *
         * <p>It also keeps {@code ImageMorphRenderer.normalOf}'s contract: the
         * {@code (0,1,0)} sentinel it emits for <i>unshaded</i> faces has to
         * saturate {@code minecraft_mix_light} at 1.0, and under the flat preset
         * it does. Under the ambient side-lit one it scored 0.40, which is why
         * an unshaded image drew dark.</p>
         *
         * <p>Set here rather than in {@code MorphRenderUtils.renderOnScreen}
         * because several callers ({@code GuiScenePanel}, {@code GuiGun},
         * {@code GuiReplaySelector}, {@code GuiModelBlockList}, the snowstorm
         * morph section) call {@code AbstractMorph.renderOnScreen} directly and
         * bypass that trap; every one of them lands here.</p>
         *
         * <p>Entity morphs are unaffected: their renderer goes through
         * {@code GuiUtils.drawEntityOnScreen}, which arms {@code method_34742}
         * for its own chain — that chain ends in {@code LivingEntityRenderer}'s
         * {@code MatrixStack.scale(-1,-1,1)}, which <i>does</i> flip the normal
         * matrix, so it needs that flipped pair and already gets it.</p>
         */
        @Override
        public void renderOnScreen(AbstractMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
        {
            IMorphRenderer<AbstractMorph> renderer = get(morph);

            if (renderer == null)
            {
                return;
            }

            DiffuseLighting.disableGuiDepthLighting();

            try
            {
                renderer.renderOnScreen(morph, player, x, y, scale, alpha);
            }
            finally
            {
                /* Back to the ambient GUI state GameRenderer armed, so the rest
                 * of the screen's item/icon draws are untouched. */
                DiffuseLighting.enableGuiDepthLighting();

                resetGuiDepth();
            }
        }

        /**
         * Put the depth buffer back the way the GUI expects to find it, so
         * anything drawn after this morph is not punched through by it.
         *
         * <p>mclib's element tree is a painter's-algorithm GUI: everything draws
         * at {@code z = 0} and later simply covers earlier. 1.12 could do that
         * because its 2D GUI draws ran with depth testing off. On 1.20.4 the
         * layer behind {@code DrawContext.fill}/text — {@code RenderLayer.GUI} —
         * is built with {@code LEQUAL_DEPTH_TEST}, and every render layer's
         * begin action re-arms {@code enableDepthTest() + depthFunc()} for
         * itself. So the depth buffer is live for 2D GUI content, and it cannot
         * be switched off from out here: parking {@code depthFunc} at
         * {@code GL_ALWAYS} (BBS {@code UIModelRenderer}'s trick, which works
         * because BBS batches through its own layer) is overwritten the moment
         * the next vanilla GUI layer begins.</p>
         *
         * <p>A morph preview is the one thing in the tree that writes real
         * depth, and it writes it <b>nearer</b> than the GUI plane — the custom
         * model chain sits at {@code z = 50}. Every later {@code fill} and glyph
         * at {@code z = 0} then fails {@code LEQUAL} wherever the model drew,
         * which is why the picker's bottom bar was coming out with morphs
         * punched through it. Clearing is the only reliable undo, and it is what
         * {@code GuiModelRenderer.setupViewport} already does for the same
         * reason on the other 3D-in-GUI path.</p>
         *
         * <p>The clear obeys the scissor test, so inside a scrolling list it
         * costs the list rect rather than the screen, and the flush that
         * precedes it is one the morph renderers already perform per preview —
         * the added cost is noise next to the model draw itself.</p>
         */
        private static void resetGuiDepth()
        {
            DrawContext context = GuiDraw.getDrawContext();

            if (context == null)
            {
                return;
            }

            /* The preview has to be rasterized before its depth is discarded;
             * geometry still sitting in the buffer would flush afterwards and
             * write the depth straight back. */
            context.draw();

            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        }

        /**
         * Unlike the two draw methods, this one has an answer even with no
         * renderer and no render frame: legacy's settings rule. Returning false
         * instead would show the player's own arm under a hand-less morph, which
         * is the opposite of what the morph asked for.
         */
        @Override
        public boolean renderHand(AbstractMorph morph, PlayerEntity player, Hand hand)
        {
            IMorphRenderer<AbstractMorph> renderer = get(morph);

            if (renderer == null || MorphRenderContext.current() == null)
            {
                return morph.claimsHandByDefault();
            }

            return renderer.renderHand(morph, player, hand);
        }
    }
}
