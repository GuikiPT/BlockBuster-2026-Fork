package mchorse.metamorph.mixin;

import mchorse.metamorph.capabilities.render.IMorphDimensionProvider;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Morph hitbox/eye-height override on the player (roadmap P54).
 *
 * <p>Serves the morph's requested {@link EntityDimensions} and eye height
 * (stored by {@code MorphDimensionHandler} via the {@code
 * AbstractMorph.sizeHandler} seam). The eye height override is only applied
 * when the morph requested it ({@code applyEye}: player + {@code disable_pov}
 * false), so it does not fight vanilla pose changes (swimming/elytra) when
 * unmorphed.</p>
 *
 * <p>SEAM(P52.1): {@link #metamorph$clearMorphSize()} is called on demorph by
 * the morph-tick handler; until then vanilla dimensions apply while unmorphed.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDimensionsMixin implements IMorphDimensionProvider
{
    @Unique
    private boolean metamorph$active;

    @Unique
    private float metamorph$width;

    @Unique
    private float metamorph$height;

    @Unique
    private float metamorph$eye;

    @Unique
    private boolean metamorph$applyEye;

    @Override
    public void metamorph$applyMorphSize(float width, float height, float eyeHeight, boolean applyEye)
    {
        this.metamorph$active = true;
        this.metamorph$width = width;
        this.metamorph$height = height;
        this.metamorph$eye = eyeHeight;
        this.metamorph$applyEye = applyEye;
    }

    @Override
    public void metamorph$clearMorphSize()
    {
        this.metamorph$active = false;
    }

    @Override
    public boolean metamorph$hasMorphSize()
    {
        return this.metamorph$active;
    }

    @Override
    public float metamorph$morphWidth()
    {
        return this.metamorph$width;
    }

    @Override
    public float metamorph$morphHeight()
    {
        return this.metamorph$height;
    }

    @Override
    public float metamorph$morphEyeHeight()
    {
        return this.metamorph$eye;
    }

    @Override
    public boolean metamorph$applyMorphEye()
    {
        return this.metamorph$applyEye;
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void metamorph$onGetDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir)
    {
        if (this.metamorph$active)
        {
            cir.setReturnValue(EntityDimensions.changing(this.metamorph$width, this.metamorph$height));
        }
    }

    @Inject(method = "getActiveEyeHeight", at = @At("HEAD"), cancellable = true)
    private void metamorph$onGetActiveEyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir)
    {
        if (this.metamorph$active && this.metamorph$applyEye)
        {
            cir.setReturnValue(this.metamorph$eye);
        }
    }
}
