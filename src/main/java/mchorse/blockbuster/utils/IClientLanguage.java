package mchorse.blockbuster.utils;

/**
 * Server-side client-language accessor for {@code ServerPlayerEntity}.
 *
 * <p>1.12.2 stored the value the client sent in {@code CPacketClientSettings}
 * on a private {@code EntityPlayerMP.language} field, which legacy Blockbuster
 * read by reflecting the entity's first {@code String} field (see
 * {@code BlockDirector.getUrl}).</p>
 *
 * <p>1.20.1 has no such field at all: {@code ServerPlayerEntity
 * .setClientSettings} keeps chat visibility, chat colors, text filtering,
 * server listing, the model-part mask and the main arm, and drops the language
 * on the floor. (The language only became server-visible state in 1.20.2, as
 * part of {@code SyncedClientOptions}.) So the port re-adds the 1.12.2 field
 * through {@code ServerPlayerEntityLanguageMixin} and reads it through this
 * duck interface — the same technique {@link IEntityPrevPrevPos} uses for the
 * fields legacy's coremod injected.</p>
 */
public interface IClientLanguage
{
    /**
     * The language the client last announced, e.g. {@code "en_us"}. Never
     * {@code null}: a player who never sent client settings (a fake player, or
     * one still mid-login) reports the vanilla default {@code "en_us"}, which
     * matches the initializer 1.12.2 gave its own field.
     */
    String blockbuster$getClientLanguage();
}
