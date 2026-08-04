package mchorse.metamorph.client.gui.overlays;

/**
 * Squid-air replacement bubble bar (roadmap P61).
 *
 * <p>1:1 port of Metamorph 1.4's {@code GuiHud}. When the current morph can't
 * breathe on land (the Swim ability with {@code hasSquidAir}), the vanilla AIR
 * overlay element is suppressed and this 300-max, 10-bubble bar is drawn in its
 * place on the right side of the hotbar. The bar only appears while
 * {@link #renderSquidAir} is set and the air value has dropped below the 300
 * maximum.</p>
 *
 * <p>Landed in S22 P225. On 1.20.4 the air bubbles are drawn inline inside the
 * private {@code InGameHud.renderStatusBars}, so there is no per-element event
 * to cancel: {@code InGameHudMixin} redirects that segment's two inputs
 * ({@code getAir()} and {@code isSubmergedIn(WATER)}) through
 * {@code MetamorphHudWiring.airBarValue}/{@code airBarSubmerged}, which read the
 * two fields below. Vanilla's own bubble arithmetic is bit-for-bit the legacy
 * math (max air 300, the same {@code ceil} counts, the same right-to-left
 * layout), so the redirect reproduces the legacy bar rather than re-drawing it.
 * The pure functions below stay as the parity record of that math (partial-bubble
 * rounding, the {@code u=25} partial icon) and are what the tests pin.</p>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/overlays/GuiHud.java
 */
public class GuiHud
{
    /** The air maximum; the bar is hidden at or above this. */
    public static final int MAX_AIR = 300;

    /** Total number of bubbles at full air. */
    public static final int BUBBLES = 10;

    /** Icon-sheet u-offset of a full bubble. */
    public static final int ICON_FULL = 16;

    /** Icon-sheet u-offset of a partial (bursting) bubble. */
    public static final int ICON_PARTIAL = 25;

    /**
     * Whether the squid-air bar should be rendered in place of the vanilla air
     * bar. Set from {@code PacketMorphState} and the per-client-tick mirror in
     * {@code MetamorphHudWiring}; read by the {@code InGameHud} redirects.
     */
    public boolean renderSquidAir = false;

    /** Current squid-air value (0–{@link #MAX_AIR}). */
    public int squidAir = MAX_AIR;

    /**
     * Whether the bar should draw for the given air value. Legacy drew only
     * when {@code squidAir < 300}.
     */
    public static boolean shouldRender(int squidAir)
    {
        return squidAir < MAX_AIR;
    }

    /**
     * Number of full bubbles for the given air value: {@code ceil((air-2)*10/300)}.
     */
    public static int fullBubbles(int squidAir)
    {
        return ceil((squidAir - 2) * 10.0D / MAX_AIR);
    }

    /**
     * Number of partial (bursting) bubbles for the given air value:
     * {@code ceil(air*10/300) - fullBubbles}.
     */
    public static int partialBubbles(int squidAir)
    {
        return ceil(squidAir * 10.0D / MAX_AIR) - fullBubbles(squidAir);
    }

    /**
     * Total bubbles drawn (full + partial) for the given air value.
     */
    public static int totalBubbles(int squidAir)
    {
        return fullBubbles(squidAir) + partialBubbles(squidAir);
    }

    /**
     * Icon-sheet u-offset for the {@code i}-th drawn bubble: full bubbles use
     * {@link #ICON_FULL}, the trailing partial bubble uses {@link #ICON_PARTIAL}.
     */
    public static int bubbleIconU(int i, int full)
    {
        return i < full ? ICON_FULL : ICON_PARTIAL;
    }

    /**
     * {@code MathHelper.ceil} equivalent (yarn's {@code MathHelper.ceil}
     * matches the legacy behaviour bit-for-bit for these inputs).
     */
    private static int ceil(double value)
    {
        int i = (int) value;

        return value > (double) i ? i + 1 : i;
    }
}
