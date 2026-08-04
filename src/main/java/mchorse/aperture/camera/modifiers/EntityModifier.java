package mchorse.aperture.camera.modifiers;

import mchorse.aperture.camera.data.Point;
import mchorse.aperture.camera.data.Position;
import mchorse.aperture.camera.values.ValuePoint;
import mchorse.mclib.config.values.ValueString;
import net.minecraft.entity.Entity;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract entity modifier (P175).
 *
 * Port notes: a bare selector (no {@code '@'}) becomes
 * {@code "@e[name=<selector>]"} — not {@code @p}; dead entities and the
 * local player are pruned on <b>every</b> application; empty result →
 * modifier inert.
 *
 * <p>Client seam: legacy resolved entities via a {@code @SideOnly(CLIENT)}
 * call into the bundled 1.12 {@code EntitySelector} over the client world.
 * Modifiers are common classes, so the client entrypoint
 * ({@code mchorse.aperture.client.ApertureClient}) installs
 * {@link #clientEntityFinder} (backed by
 * {@code mchorse.aperture.utils.EntitySelector}) and {@link #clientPlayer};
 * headless defaults leave {@link #entities} null (inert), keeping the class
 * plain-JUnit testable.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/modifiers/EntityModifier.java
 */
public abstract class EntityModifier extends AbstractModifier
{
    /**
     * Client-side selector engine (see class javadoc). Receives the full
     * (already {@code @e[name=…]}-wrapped) selector token; returns matched
     * entities or throws on malformed selectors.
     */
    public static Function<String, List<Entity>> clientEntityFinder;

    /**
     * The local (client) player — pruned from matches like legacy.
     */
    public static Supplier<Entity> clientPlayer = () -> null;

    /**
     * Position which may be used for calculation of relative
     * camera fixture animations
     */
    public Position position = new Position(0, 0, 0, 0, 0);

    /**
     * Target entity
     */
    public List<Entity> entities;

    /**
     * Target (entity) selector
     *
     * @link https://minecraft.gamepedia.com/Commands#Target_selector_variables
     */
    public final ValueString selector = new ValueString("selector", "");
    public final ValuePoint offset = new ValuePoint("offset", new Point(0, 0, 0));

    public EntityModifier()
    {
        super();

        this.register(this.selector);
        this.register(this.offset);
    }

    /**
     * Try finding entity based on entity selector or target's UUID
     */
    public void tryFindingEntity()
    {
        this.entities = null;

        String selector = this.selector.get();

        if (selector != null && !selector.isEmpty() && clientEntityFinder != null)
        {
            this.tryFindingEntityClient(selector);
        }
    }

    private void tryFindingEntityClient(String selector)
    {
        if (!selector.contains("@"))
        {
            selector = "@e[name=" + selector + "]";
        }

        try
        {
            this.entities = clientEntityFinder.apply(selector);

            if (this.entities != null && this.entities.isEmpty())
            {
                this.entities = null;
            }
        }
        catch (Exception e)
        {
            this.entities = null;
        }
    }

    /**
     * Check for dead entities
     */
    protected boolean checkForDead()
    {
        if (this.entities == null)
        {
            return true;
        }

        Iterator<Entity> it = this.entities.iterator();

        while (it.hasNext())
        {
            Entity entity = it.next();

            /* P277: legacy `entity.isDead` — the field. `!isAlive()` looks
             * equivalent and is, for a plain Entity; but this list holds
             * LivingEntity instances too, and LivingEntity overrides isAlive()
             * to also require health > 0. Through virtual dispatch that pruned a
             * dying-but-present entity out of the camera's target list ~20 ticks
             * early, jerking the shot off it before it finished dying. */
            if (entity.isRemoved() || entity == clientPlayer.get())
            {
                it.remove();
            }
        }

        if (this.entities.isEmpty())
        {
            this.entities = null;
        }

        return this.entities == null;
    }
}
