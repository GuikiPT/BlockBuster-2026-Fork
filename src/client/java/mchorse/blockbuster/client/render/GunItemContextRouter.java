package mchorse.blockbuster.client.render;

import net.minecraft.client.render.model.json.ModelTransformationMode;

/**
 * Pure (no-GL) port of the transform-context routing in the legacy
 * {@code TileEntityGunItemStackRenderer.renderByItem} (P197): given the camera
 * {@link ModelTransformationMode} and the zoom / morph flags, decide which of
 * the four gun render calls ({@code renderHands}, {@code renderZoomOverlay},
 * {@code render}, {@code renderInventoryMorph}) fire.
 *
 * <p>The legacy routing is <b>exclusion-list</b> shaped, not inclusion-list —
 * the "first-person-like" bucket is "everything that is not
 * GUI / THIRD_PERSON / FIXED / GROUND" — and this port keeps that shape so new
 * 1.20.4 modes ({@code NONE}, {@code HEAD}) fall into the same bucket the legacy
 * logic would have put them in. Load-bearing quirks preserved:</p>
 *
 * <ul>
 *   <li>hands render in the first-person-like bucket unless
 *       {@code zooming &amp;&amp; hideHandsOnZoom};</li>
 *   <li>zoom overlay renders in that bucket when
 *       {@code useZoomOverlayMorph &amp;&amp; zooming};</li>
 *   <li>the gun morph renders in every non-GUI mode, except first-person while
 *       {@code zooming &amp;&amp; hideHandsOnZoom} (the gun hides with the hands);</li>
 *   <li>GUI renders the inventory morph when
 *       {@code useInventoryMorph &amp;&amp; inventoryMorph != null}, else the gun morph.</li>
 * </ul>
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/tileentity/TileEntityGunItemStackRenderer.java}.
 */
public final class GunItemContextRouter
{
    private GunItemContextRouter()
    {}

    /** Which of the four gun render calls fire for a given context. */
    public static final class Decision
    {
        public final boolean hands;
        public final boolean zoomOverlay;
        public final boolean gun;
        public final boolean inventory;

        public Decision(boolean hands, boolean zoomOverlay, boolean gun, boolean inventory)
        {
            this.hands = hands;
            this.zoomOverlay = zoomOverlay;
            this.gun = gun;
            this.inventory = inventory;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Decision))
            {
                return false;
            }

            Decision d = (Decision) obj;

            return this.hands == d.hands && this.zoomOverlay == d.zoomOverlay && this.gun == d.gun && this.inventory == d.inventory;
        }

        @Override
        public int hashCode()
        {
            return (this.hands ? 1 : 0) | (this.zoomOverlay ? 2 : 0) | (this.gun ? 4 : 0) | (this.inventory ? 8 : 0);
        }

        @Override
        public String toString()
        {
            return "Decision{hands=" + this.hands + ", zoomOverlay=" + this.zoomOverlay + ", gun=" + this.gun + ", inventory=" + this.inventory + "}";
        }
    }

    /** True for the two first-person hand transforms. */
    public static boolean isFirstPerson(ModelTransformationMode mode)
    {
        return mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
            || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
    }

    /**
     * The legacy "first-person-like" bucket: everything that is not
     * GUI / THIRD_PERSON / FIXED / GROUND.
     */
    public static boolean isFirstPersonLike(ModelTransformationMode mode)
    {
        return mode != ModelTransformationMode.GUI
            && mode != ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
            && mode != ModelTransformationMode.THIRD_PERSON_LEFT_HAND
            && mode != ModelTransformationMode.FIXED
            && mode != ModelTransformationMode.GROUND;
    }

    /**
     * Route a render context to the four render-call decisions, mirroring the
     * three sequential legacy branches exactly.
     */
    public static Decision route(ModelTransformationMode mode, boolean zooming, boolean hideHandsOnZoom, boolean useZoomOverlayMorph, boolean useInventoryMorph, boolean inventoryMorphPresent)
    {
        boolean firstPerson = isFirstPerson(mode);
        boolean hands = false;
        boolean zoomOverlay = false;
        boolean gun = false;
        boolean inventory = false;

        /* Branch 1: first-person-like — hands + zoom overlay. */
        if (isFirstPersonLike(mode))
        {
            if (!(zooming && hideHandsOnZoom))
            {
                hands = true;
            }

            if (useZoomOverlayMorph && zooming)
            {
                zoomOverlay = true;
            }
        }

        /* Branch 2: any non-GUI mode renders the gun morph, except first-person
         * while hiding the hands on zoom (the gun hides with the hands). */
        if (mode != ModelTransformationMode.GUI)
        {
            if (zooming && hideHandsOnZoom)
            {
                if (!firstPerson)
                {
                    gun = true;
                }
            }
            else
            {
                gun = true;
            }
        }

        /* Branch 3: GUI renders the inventory morph (or the gun as fallback). */
        if (mode == ModelTransformationMode.GUI)
        {
            if (useInventoryMorph && inventoryMorphPresent)
            {
                inventory = true;
            }
            else
            {
                gun = true;
            }
        }

        return new Decision(hands, zoomOverlay, gun, inventory);
    }
}
