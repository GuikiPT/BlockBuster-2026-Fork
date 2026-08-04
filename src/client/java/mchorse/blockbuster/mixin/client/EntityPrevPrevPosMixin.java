package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.utils.IEntityPrevPrevPos;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two-tick entity position history for the Snowstorm collision component
 * (roadmap P151.1).
 *
 * <p>Replaces the 1.12.2 {@code EntityTransformer} coremod, which ASM-injected
 * {@code public double prevPrevPosX/Y/Z} onto {@code Entity} and assigned them
 * from {@code prevPos*} at the start of {@code onEntityUpdate}. Yarn's
 * {@link Entity#baseTick()} is the {@code onEntityUpdate} equivalent, and vanilla
 * rolls {@code prevX/prevY/prevZ} forward later in the same tick — so copying at
 * {@code HEAD} keeps the history exactly one tick behind {@code prevPos*}, as the
 * legacy insertion point did. Doing it any later would collapse the two-tick gap
 * and make the collision inertia kick double or zero out.</p>
 *
 * <p>Client-only: the Snowstorm engine runs entirely client-side, so only client
 * entities need the history. Entities without the mixin (server side, or objects
 * in a plain unit test) fall back to {@code 0.0} through the {@code instanceof}
 * guard in {@code EntityTransformationUtils} — the same as an un-transformed
 * 1.12.2 path.</p>
 */
@Mixin(Entity.class)
public abstract class EntityPrevPrevPosMixin implements IEntityPrevPrevPos
{
    @Unique
    private double blockbuster$prevPrevPosX;

    @Unique
    private double blockbuster$prevPrevPosY;

    @Unique
    private double blockbuster$prevPrevPosZ;

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void blockbuster$capturePrevPrevPos(CallbackInfo info)
    {
        Entity self = (Entity) (Object) this;

        this.blockbuster$prevPrevPosX = self.prevX;
        this.blockbuster$prevPrevPosY = self.prevY;
        this.blockbuster$prevPrevPosZ = self.prevZ;
    }

    @Override
    public double blockbuster$getPrevPrevPosX()
    {
        return this.blockbuster$prevPrevPosX;
    }

    @Override
    public double blockbuster$getPrevPrevPosY()
    {
        return this.blockbuster$prevPrevPosY;
    }

    @Override
    public double blockbuster$getPrevPrevPosZ()
    {
        return this.blockbuster$prevPrevPosZ;
    }
}
