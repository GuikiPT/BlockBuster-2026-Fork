package mchorse.blockbuster.client.video;

/**
 * Green-screen ("chroma") sky decision + colour-unpack core (P203), kept GL-free
 * so the whole decision is headlessly testable against a recorded fake of the GL
 * facade ({@link Gl}). The real GL implementation lives in
 * {@code mchorse.blockbuster.client.RenderingHandler}; the mixin
 * ({@code WorldRendererMixin}) only asks {@code isGreenSky()} then delegates to
 * {@code renderGreenSky()}.
 *
 * <p>Faithful port of legacy
 * {@code mchorse.blockbuster.client.RenderingHandler.renderGreenSky()} (1.12.2):
 * the {@code green_screen_sky_color} config is an <b>ARGB</b> int
 * (McLib {@code colorAlpha()} subtype, default {@code 0xff00ff00} = opaque green),
 * unpacked as {@code R = >>16, G = >>8, B = &0xff, A = >>24}. Legacy cleared only
 * the colour buffer with that colour and then {@code glDisable(GL_FOG)}; on the
 * core profile there is no fixed-function fog, so the fog <i>colour</i> is set to
 * the chroma colour instead (BBS technique) — distant terrain stops being tinted
 * toward the vanilla sky colour without disabling fog density.</p>
 *
 * <p>The alpha channel was inert on 1.12.2 (it only fed {@code clearColor}); it
 * becomes the real transparency source in the port's alpha-capture mode (P203
 * addition), which is why {@code colorAlpha()} + the {@code A} unpack are
 * load-bearing and must not be stripped.</p>
 */
public final class ChromaSky
{
    private ChromaSky()
    {}

    /**
     * Minimal GL facade the chroma-sky clear needs. The production impl calls
     * {@code RenderSystem}; tests record the calls.
     */
    public interface Gl
    {
        void clearColor(float r, float g, float b, float a);

        /** Clear the colour buffer only (legacy cleared {@code GL_COLOR_BUFFER_BIT}). */
        void clearColorBuffer();

        /** Core-profile stand-in for legacy {@code glDisable(GL_FOG)}. */
        void setShaderFogColor(float r, float g, float b, float a);
    }

    /** Red component of an ARGB int as a 0..1 float (legacy {@code >> 16 & 0xff}). */
    public static float red(int argb)
    {
        return (argb >> 16 & 0xff) / 255F;
    }

    /** Green component (legacy {@code >> 8 & 0xff}). */
    public static float green(int argb)
    {
        return (argb >> 8 & 0xff) / 255F;
    }

    /** Blue component (legacy {@code & 0xff}). */
    public static float blue(int argb)
    {
        return (argb & 0xff) / 255F;
    }

    /** Alpha component (legacy {@code >> 24 & 0xff}) — the transparency source in alpha mode. */
    public static float alpha(int argb)
    {
        return (argb >> 24 & 0xff) / 255F;
    }

    /**
     * Perform the chroma-sky clear for the given ARGB colour: clear the colour
     * buffer to {@code (r, g, b, a)} and pin the shader fog colour to the same
     * colour. Order (clear colour → clear → fog) mirrors the legacy prologue; the
     * caller cancels the rest of {@code renderSky} so no sky geometry composites
     * over the flat colour (translucent world content still does — parity).
     */
    public static void render(Gl gl, int argb)
    {
        float r = red(argb);
        float g = green(argb);
        float b = blue(argb);
        float a = alpha(argb);

        gl.clearColor(r, g, b, a);
        gl.clearColorBuffer();
        gl.setShaderFogColor(r, g, b, a);
    }
}
