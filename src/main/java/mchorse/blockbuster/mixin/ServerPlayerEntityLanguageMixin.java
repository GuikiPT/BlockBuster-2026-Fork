package mchorse.blockbuster.mixin;

import mchorse.blockbuster.utils.IClientLanguage;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-adds 1.12.2's {@code EntityPlayerMP.language} field, which 1.20.1 dropped.
 *
 * <p>{@code BlockDirector} picks the Bilibili or the YouTube tutorial link off
 * the viewer's language ({@code zh_*} → Bilibili). Legacy read that from the
 * private {@code language} field vanilla assigned in
 * {@code handleClientSettings}; 1.20.1's {@code setClientSettings} keeps every
 * other field of the packet but not the language, so the value is captured here
 * at the same point in the same method and handed back through
 * {@link IClientLanguage}. Behavioral output is identical.</p>
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityLanguageMixin implements IClientLanguage
{
    /** Vanilla's own default, matching 1.12.2's field initializer. */
    @Unique
    private String blockbuster$language = "en_us";

    @Override
    public String blockbuster$getClientLanguage()
    {
        return this.blockbuster$language;
    }

    @Inject(method = "setClientSettings", at = @At("TAIL"))
    private void blockbuster$captureLanguage(ClientSettingsC2SPacket packet, CallbackInfo info)
    {
        this.blockbuster$language = packet.language();
    }
}
