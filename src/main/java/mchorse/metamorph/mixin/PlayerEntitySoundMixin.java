package mchorse.metamorph.mixin;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.entity.SoundHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Morph hurt/death/step sound replacement (roadmap P54.2).
 *
 * <p>Injects the {@code PlayerEntity.getHurtSound}/{@code getDeathSound}/{@code
 * playStepSound} calls to serve the current morph's sounds (see {@link
 * SoundHandler}). Inert until the P52 morphing component wires {@code
 * SoundHandler.provider}.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySoundMixin
{
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void metamorph$onGetHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir)
    {
        PlayerEntity self = (PlayerEntity) (Object) this;
        AbstractMorph morph = SoundHandler.currentMorph(self);

        if (morph == null)
        {
            return;
        }

        SoundHandler.Resolution resolution = SoundHandler.resolveHurt(morph, self, source);

        if (resolution.decision == SoundHandler.Decision.SUPPRESS)
        {
            cir.setReturnValue(null);
        }
        else if (resolution.decision == SoundHandler.Decision.REPLACE)
        {
            cir.setReturnValue(resolution.sound);
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void metamorph$onGetDeathSound(CallbackInfoReturnable<SoundEvent> cir)
    {
        PlayerEntity self = (PlayerEntity) (Object) this;
        AbstractMorph morph = SoundHandler.currentMorph(self);

        if (morph == null)
        {
            return;
        }

        SoundHandler.Resolution resolution = SoundHandler.resolveDeath(morph, self);

        if (resolution.decision == SoundHandler.Decision.SUPPRESS)
        {
            cir.setReturnValue(null);
        }
        else if (resolution.decision == SoundHandler.Decision.REPLACE)
        {
            cir.setReturnValue(resolution.sound);
        }
    }

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void metamorph$onPlayStepSound(BlockPos pos, BlockState state, CallbackInfo ci)
    {
        PlayerEntity self = (PlayerEntity) (Object) this;
        AbstractMorph morph = SoundHandler.currentMorph(self);

        if (morph == null)
        {
            return;
        }

        boolean mismatch = SoundHandler.hitboxMismatch(morph, self);
        SoundHandler.StepDecision decision = SoundHandler.resolveStep(morph, self, mismatch);

        if (decision == SoundHandler.StepDecision.SUPPRESS)
        {
            ci.cancel();
        }
        else if (decision == SoundHandler.StepDecision.CUSTOM)
        {
            ci.cancel();
            morph.playStepSound(self);
        }
    }
}
