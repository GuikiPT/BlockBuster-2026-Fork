package mchorse.vanilla_pack.morphs;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;

/**
 * Placeholder player morph entity (roadmap P54).
 *
 * <p>This player entity is used for overriding some of its methods to provide
 * stable functionality of player morph in its isolated environment: it is not
 * in the player list, is not tracked by the network handler, and would
 * otherwise fall back to the default skin.</p>
 *
 * <p><b>Legacy → 1.20.4.</b> 1.12.2 answered the renderer through three
 * separate hooks — {@code getSkinType()}, {@code getLocationSkin()},
 * {@code getLocationCape()} — plus {@code hasPlayerInfo()} as the cape gate.
 * 1.20.2 collapsed all four into the single {@link SkinTextures} record
 * returned by {@link #getSkinTextures()}, so the three overrides become one
 * and {@code hasPlayerInfo} disappears (the cape layer now gates on
 * {@code capeTexture() != null}, which the record already carries). The
 * lazily-built {@link PlayerListEntry} is the direct stand-in for legacy's
 * lazily-built {@code NetworkPlayerInfo}: its texture supplier is memoized and
 * only touches the skin service on the first call, so constructing the morph
 * costs nothing.</p>
 *
 * <p>Kept in the {@code mchorse.vanilla_pack.morphs} package, split across the
 * source sets, because legacy declared it as a {@code @SideOnly(CLIENT)} nested
 * class of {@code PlayerMorph} — which a common-side class cannot hold here.
 * That morph is gone (a player disguise is an ordinary
 * {@link mchorse.metamorph.api.morphs.EntityMorph} named
 * {@code minecraft:player} now), so the seam that reaches this class is
 * {@link mchorse.metamorph.api.morphs.EntityMorph#clientPlayerFactory}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/PlayerMorph.java (PlayerMorphClientEntity)
 */
public class PlayerMorphClientEntity extends OtherClientPlayerEntity
{
    public GameProfile profile;
    public boolean isBaby;
    public String skinType = "";

    /**
     * Player's network info
     */
    public PlayerListEntry info;

    public PlayerMorphClientEntity(ClientWorld world, GameProfile profile)
    {
        super(world, profile);

        this.profile = profile;
    }

    /**
     * Initiate network info property thing
     */
    protected void initiateNetworkInfo()
    {
        if (this.info == null)
        {
            /* false = chat is not secure; legacy's NetworkPlayerInfo(profile)
             * had no chat session either. */
            this.info = new PlayerListEntry(this.profile, false);
        }
    }

    @Override
    public boolean isBaby()
    {
        return this.isBaby;
    }

    /**
     * Get this player's skin, cape and arm model.
     *
     * <p>The stored {@code SkinType} overrides only the arm model, exactly as
     * legacy's {@code getSkinType()} did — {@code "alex"} → slim, anything else
     * non-empty → default/wide, empty → whatever the profile resolved to.</p>
     */
    @Override
    public SkinTextures getSkinTextures()
    {
        this.initiateNetworkInfo();

        SkinTextures textures = this.info.getSkinTextures();

        if (this.skinType.isEmpty())
        {
            return textures;
        }

        SkinTextures.Model model = this.skinType.equals("alex") ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;

        if (model == textures.model())
        {
            return textures;
        }

        return new SkinTextures(textures.texture(), textures.textureUrl(), textures.capeTexture(), textures.elytraTexture(), model, textures.secure());
    }

    /**
     * This player is always wearing every part of the body
     */
    @Override
    public boolean isPartVisible(PlayerModelPart part)
    {
        return true;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound compound)
    {
        super.readCustomDataFromNbt(compound);

        this.isBaby = compound.getBoolean("IsBaby");
        this.skinType = compound.getString("SkinType");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound compound)
    {
        super.writeCustomDataToNbt(compound);

        compound.putBoolean("IsBaby", this.isBaby);
        compound.putString("SkinType", this.skinType);
    }
}
