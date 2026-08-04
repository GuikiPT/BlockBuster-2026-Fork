package mchorse.mclib.utils.resources;

/**
 * Texture location
 *
 * Legacy javadoc: "A hack class that allows to use uppercase characters in the
 * path 1.11.2 and up." — in 1.12.2 this class used reflection to unlock
 * vanilla ResourceLocation's final fields and overwrite them with the
 * original-case strings. The bundled {@link ResourceLocation} base preserves
 * case and keeps its fields mutable, so {@link #set} assigns directly; the
 * reflection hack is gone but the observable behavior (including the
 * {@code split(":")} quirks of {@link #set(String)}) is identical.
 */
public class TextureLocation extends ResourceLocation
{
    public TextureLocation(String domain, String path)
    {
        super(domain, path);

        this.set(domain, path);
    }

    public TextureLocation(String string)
    {
        super(string);

        this.set(string);
    }

    /**
     * Legacy quirk (load-bearing): this uses {@code String.split(":")}, so a
     * colonless string becomes the <b>domain</b> with an empty path
     * ({@code "foo"} → {@code "foo:"}), and anything past a second colon is
     * dropped — unlike the base constructor's first-colon split.
     */
    public void set(String location)
    {
        String[] split = location.split(":");
        String domain = split.length > 0 ? split[0] : "minecraft";
        String path = split.length > 1 ? split[1] : "";

        this.set(domain, path);
    }

    public void set(String domain, String path)
    {
        /* Guess what it does (without reflection these days) */
        this.domain = domain;
        this.path = path;
    }
}
