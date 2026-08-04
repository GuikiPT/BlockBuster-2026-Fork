package mchorse.vanilla_pack.morphs;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.PlayerModelPart;
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
 * <p><b>Legacy → 1.20.1.</b> 1.12.2 answered the renderer through three
 * separate hooks — {@code getSkinType()}, {@code getLocationSkin()},
 * {@code getLocationCape()} — plus {@code hasPlayerInfo()} as the cape gate.
 * 1.20.1 keeps that same four-way shape, and every one of those methods on
 * {@link net.minecraft.client.network.AbstractClientPlayerEntity} reads through
 * a single {@code getPlayerListEntry()} lookup, so overriding <i>that</i>
 * answers all four at once — legacy's {@code hasPlayerInfo}/{@code getPlayerInfo}
 * seam exactly. Only {@link #getModel()} needs its own override, for the
 * {@code skinType} arm-model force. (1.20.2 later collapsed the four into one
 * {@code SkinTextures} record; that consolidation does not exist here.) The
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
     * The one seam every skin/cape/elytra/arm-model lookup on
     * {@code AbstractClientPlayerEntity} goes through. Vanilla resolves it out
     * of the client's player list, where this morph does not appear (which is
     * why it would otherwise render with the default skin); pointing it at the
     * morph's own entry is legacy's {@code hasPlayerInfo}/{@code getPlayerInfo}
     * override.
     */
    @Override
    protected PlayerListEntry getPlayerListEntry()
    {
        this.initiateNetworkInfo();

        return this.info;
    }

    /**
     * Get this player's arm model.
     *
     * <p>The stored {@code SkinType} overrides only the arm model, exactly as
     * legacy's {@code getSkinType()} did — {@code "alex"} → slim, anything else
     * non-empty → default/wide, empty → whatever the profile resolved to.</p>
     */
    @Override
    public String getModel()
    {
        if (this.skinType.isEmpty())
        {
            return super.getModel();
        }

        return this.skinType.equals("alex") ? "slim" : "default";
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
