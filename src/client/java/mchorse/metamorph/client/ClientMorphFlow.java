package mchorse.metamorph.client;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.client.gui.overlays.GuiHud;
import mchorse.metamorph.client.gui.overlays.GuiOverlay;

/**
 * Client-side apply logic for the survival morph packets (roadmap P61).
 *
 * <p>Ports the non-render behaviour of Metamorph 1.4's client packet handlers
 * so it can be exercised headlessly, decoupled from the actual packet transport
 * (P55) and the HUD render surface:</p>
 *
 * <ul>
 *   <li>{@link #acquire(IMorphing, GuiOverlay, AbstractMorph)} — the
 *       {@code ClientHandlerAcquireMorph} flow: add the morph to the player's
 *       acquired list <em>and</em> push an acquired toast. Legacy pushed the
 *       toast unconditionally, even for a duplicate acquire, so that quirk is
 *       preserved.</li>
 *   <li>{@link #applyMorphState(IMorphing, GuiHud, boolean, int)} — the
 *       {@code ClientHandlerMorphState} flow: copy the squid-air state onto the
 *       morphing capability and mirror it onto the HUD widget. The legacy handler
 *       also overwrote the client morph entity's {@code entityId} — that is an
 *       EntityMorph/entity-tracking concern and stays a SEAM (the entity-id
 *       rewrite lands with the P55 packet wiring / EntityMorph client entity).</li>
 * </ul>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/creative/ClientHandlerAcquireMorph.java
 * and .../network/client/survival/ClientHandlerMorphState.java
 */
public final class ClientMorphFlow
{
    private ClientMorphFlow()
    {}

    /**
     * Apply an acquire-morph packet on the client: acquire the morph on the
     * capability, then push an acquired toast onto the overlay (unconditionally,
     * matching legacy).
     */
    public static void acquire(IMorphing morphing, GuiOverlay overlay, AbstractMorph morph)
    {
        if (morphing != null)
        {
            morphing.acquireMorph(morph);
        }

        if (overlay != null)
        {
            overlay.add(morph);
        }
    }

    /**
     * Apply a morph-state packet on the client: copy the squid-air flags onto
     * the capability and mirror them onto the HUD widget so the replacement air
     * bar renders (or not).
     */
    public static void applyMorphState(IMorphing morphing, GuiHud hud, boolean hasSquidAir, int squidAir)
    {
        if (morphing != null)
        {
            morphing.setHasSquidAir(hasSquidAir);
            morphing.setSquidAir(squidAir);
        }

        if (hud != null)
        {
            hud.renderSquidAir = hasSquidAir;
            hud.squidAir = squidAir;
        }
    }
}
