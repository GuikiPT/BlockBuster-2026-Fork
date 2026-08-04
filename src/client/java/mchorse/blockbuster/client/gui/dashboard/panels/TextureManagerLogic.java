package mchorse.blockbuster.client.gui.dashboard.panels;

import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.Map;

/**
 * Pure, GL-free decision logic extracted out of {@link GuiTextureManagerPanel}
 * (roadmap P140) so the list-fill / sort / re-select flow, the replace-alias
 * behavior, the remove index-decrement quirk and the min/mag filter constant
 * table can be verified headlessly (the panel's GL calls cannot).
 *
 * <p>The legacy panel kept all of this inline in
 * {@code GuiTextureManagerPanel}; the port routes both the real panel and the
 * unit tests through these static helpers so the tested logic is the shipped
 * logic, not a re-implementation.</p>
 */
public final class TextureManagerLogic
{
    private TextureManagerLogic()
    {}

    /* ------------------------------------------------------------------ *
     *  Filter constant table (verified against legacy setLinear)          *
     * ------------------------------------------------------------------ */

    /**
     * Legacy {@code setLinear} min-filter table. Note the non-linear + mipmap
     * combo is {@code GL_NEAREST_MIPMAP_LINEAR} (<b>not</b> {@code _NEAREST}) —
     * copy the constant exactly, it is load-bearing.
     */
    public static int minFilter(boolean linear, boolean mipmap)
    {
        return linear
            ? (mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR)
            : (mipmap ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST);
    }

    /**
     * Legacy {@code setLinear} mag-filter: mipmapping never affects the mag
     * filter (the {@code *_MIPMAP_*} enums are invalid for magnification).
     */
    public static int magFilter(boolean linear)
    {
        return linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
    }

    /**
     * Legacy {@code pickRL} linear detection: reads the current mag filter and
     * classifies the texture as "linear" for the three linear-family enums.
     */
    public static boolean isLinear(int magFilter)
    {
        return magFilter == GL11.GL_LINEAR
            || magFilter == GL11.GL_LINEAR_MIPMAP_LINEAR
            || magFilter == GL11.GL_LINEAR_MIPMAP_NEAREST;
    }

    /* ------------------------------------------------------------------ *
     *  Identifier <-> bundled ResourceLocation at the texture-map boundary*
     * ------------------------------------------------------------------ */

    /**
     * Vanilla texture-map keys are {@link Identifier}s; the list element type
     * is the bundled {@link ResourceLocation}. Vanilla ids are already valid,
     * so this round-trips losslessly with {@link #toIdentifier(ResourceLocation)}.
     */
    public static ResourceLocation toResourceLocation(Identifier id)
    {
        return new ResourceLocation(id.getNamespace(), id.getPath());
    }

    public static Identifier toIdentifier(ResourceLocation rl)
    {
        return rl.toIdentifier();
    }

    /* ------------------------------------------------------------------ *
     *  List and map operations                                            *
     * ------------------------------------------------------------------ */

    /**
     * Legacy {@code open()} list fill: clear, add every texture key (converted),
     * sort and update. Re-selection of the previously picked id is done by the
     * caller afterwards (legacy did {@code setCurrent(this.rl)}).
     */
    public static void fill(GuiListElement<ResourceLocation> list, Collection<Identifier> keys)
    {
        list.clear();

        for (Identifier id : keys)
        {
            list.getList().add(toResourceLocation(id));
        }

        list.sort();
        list.update();
    }

    /**
     * Legacy {@code replace(String)} aliasing: no-op when the target equals the
     * current id, otherwise point the current id at the <em>other</em> id's
     * texture object (two ids, one GL texture). Returns the aliased texture, or
     * {@code null} when nothing changed.
     *
     * <p><b>Hazard (legacy, reproduced):</b> after aliasing, removing either id
     * deletes the shared GL id, leaving the other id dangling. The legacy panel
     * never guarded against this and neither do we.</p>
     */
    public static <K, V> V alias(Map<K, V> map, K current, K other)
    {
        if (current.equals(other))
        {
            return null;
        }

        V texture = map.get(other);

        if (texture != null)
        {
            map.put(current, texture);
        }

        return texture;
    }

    /**
     * Legacy {@code remove()} tail: drop the element from the list then
     * {@code setIndex(getIndex() - 1)} and report the new current-first for the
     * re-pick. The GL delete + map removal happen in the panel (they need a GL
     * context); this is the headless-testable index bookkeeping.
     */
    public static ResourceLocation removeAndReindex(GuiListElement<ResourceLocation> list, ResourceLocation rl)
    {
        list.remove(rl);
        list.setIndex(list.getIndex() - 1);

        return list.getCurrentFirst();
    }
}
