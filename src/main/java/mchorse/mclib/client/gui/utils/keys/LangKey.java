package mchorse.mclib.client.gui.utils.keys;

import java.util.function.BiFunction;

/**
 * Port of McLib 2.4.3's {@code LangKey} (roadmap P8). 1.12.2
 * {@code I18n.format} maps to yarn's client
 * {@code net.minecraft.client.resource.language.I18n.translate}. The
 * {@code lastTime} frame-cache is ticked by the client entrypoint once GUI
 * stages land; until then {@code lastTime} stays 0 and each key translates
 * once (time -1 < 0) — acceptable pre-GUI behavior recorded in the plan.
 *
 * Note the legacy asymmetry in {@link #set(String)}: it eagerly re-translates
 * WITHOUT the stored args — kept verbatim, do not "improve".
 *
 * <p>S2 (P26) note: this class moved to the <b>main</b> source set because
 * {@code KeyParser.keyTo/FromBytes} runs inside {@code PacketConfirm} on the
 * dedicated server too. The only client-touching call ({@code I18n.translate})
 * is isolated behind {@link #translator}: the client entrypoint installs the
 * real {@code I18n::translate}; the headless/server default echoes the raw
 * key (total-reader safe — 1.12.2 never translated keys server-side).</p>
 */
public class LangKey implements IKey
{
    /**
     * Side-aware translation seam. Installed by {@code BlockbusterClient}
     * with {@code I18n::translate}; defaults to key-echo on the server and
     * in headless tests.
     */
    public static BiFunction<String, Object[], String> translator = (key, args) -> key;

    public static long lastTime;

    public String key;
    public String string;
    public long time = -1;
    public Object[] args = new Object[0];

    public LangKey(String key)
    {
        this.key = key;
    }

    public LangKey args(Object... args)
    {
        this.args = args;

        return this;
    }

    public String update()
    {
        this.time = -1;

        return this.get();
    }

    @Override
    public String get()
    {
        if (lastTime > time)
        {
            this.time = lastTime;
            this.string = translator.apply(this.key, this.args);
        }

        return this.string;
    }

    @Override
    public void set(String string)
    {
        this.key = string;
        this.string = translator.apply(this.key, new Object[0]);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof LangKey)
        {
            return this.get().equals(((LangKey) obj).get());
        }

        return false;
    }

    @Override
    public String toString()
    {
        return this.get();
    }
}
