package mchorse.metamorph.api.creative.sections;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.JsonUtils;
import mchorse.mclib.utils.PastCopies;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.categories.AcquiredCategory;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.categories.RecentCategory;
import mchorse.metamorph.api.creative.categories.UserCategory;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.world.World;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * User morph section (roadmap P57).
 *
 * <p>Stores acquired morphs, recently edited morphs and custom global
 * categories created by the player. The global categories persist to
 * {@code config/metamorph/list.json} as a JSON array of
 * {@code {title, hidden, morphs: [<Mojangson nbt string>...]}} objects, loaded
 * lazily on the first picker open and saved on every category add/edit/remove
 * and on reset/world exit. Unparseable morph entries print a stacktrace and are
 * skipped (dropped on the next save) — a total reader that never crashes.</p>
 *
 * <p>Port notes vs legacy:</p>
 * <ul>
 *   <li>{@code list.json} location comes from {@link #file} (default
 *       {@code config/metamorph/list.json}), overridable for tests, replacing
 *       legacy {@code Metamorph.proxy.list}.</li>
 *   <li>The acquired-morph list is pulled from the client player's morphing
 *       component in legacy; that client access is a seam
 *       ({@link #acquiredMorphsSupplier}, default empty) filled by the picker /
 *       survival phase.</li>
 *   <li>Morph NBT strings are read with {@link StringNbtReader} and written via
 *       {@link net.minecraft.nbt.NbtCompound#toString()} (Mojangson), matching
 *       the legacy {@code JsonToNBT}/{@code toString()} round-trip.</li>
 *   <li>Loads/saves still log via {@code System.out.println} to match legacy.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/sections/UserSection.java
 */
public class UserSection extends MorphSection
{
    /**
     * SEAM(P58/P61): supplies the client player's acquired morphs on picker
     * update. Default empty so the section is headless-constructible.
     */
    public static Supplier<List<AbstractMorph>> acquiredMorphsSupplier = Collections::emptyList;

    public AcquiredCategory acquired;
    public RecentCategory recent;

    public boolean loaded = false;
    public List<UserCategory> global = new ArrayList<UserCategory>();

    /** {@code config/metamorph/list.json}; overridable for tests. */
    public File file;

    public UserSection(String title)
    {
        super(title);

        this.acquired = new AcquiredCategory(this, "acquired");
        this.recent = new RecentCategory(this, "recent");
        this.file = FabricLoader.getInstance().getConfigDir().resolve("metamorph").resolve("list.json").toFile();
    }

    @Override
    public void add(MorphCategory category)
    {
        super.add(category);

        if (category instanceof UserCategory)
        {
            this.global.add((UserCategory) category);
        }

        this.save();
    }

    @Override
    public void remove(MorphCategory category)
    {
        super.remove(category);

        if (category instanceof UserCategory)
        {
            this.global.remove(category);
        }

        this.save();
    }

    @Override
    public void update(World world)
    {
        super.update(world);

        List<AbstractMorph> acquiredMorphs = acquiredMorphsSupplier.get();

        this.categories.clear();
        this.categories.add(this.acquired);
        this.categories.add(this.recent);
        this.acquired.setMorphs(acquiredMorphs == null ? Collections.emptyList() : acquiredMorphs);

        if (!this.loaded)
        {
            /* P284: `loaded` is the gate on save(). Legacy set it
             * unconditionally, so a load() that threw half-way through a
             * corrupt list.json left `global` empty AND armed saving — the next
             * category the user added rewrote list.json as a one-element array
             * and every other category was gone. load() now reports whether it
             * actually read the file; a failed read leaves saving disarmed, so
             * the file the user still has on disk survives the session. */
            this.loaded = this.load();
        }

        this.categories.addAll(this.global);
    }

    @Override
    public void reset()
    {
        super.reset();

        if (this.loaded)
        {
            this.save();
            this.loaded = false;
        }

        this.categories.clear();
        this.acquired.setMorphs(Collections.emptyList());
        this.recent.clear();
        this.global.clear();
    }

    /* SEAM(P58): getGUI(...) constructing GuiUserSection is client-only. */

    /**
     * Read {@code config/metamorph/list.json} into {@link #global}.
     *
     * <p><b>P284:</b> returns whether the load can be trusted. {@code false}
     * means the file exists but could not be read, and {@link #save()} must stay
     * disarmed — writing the empty in-memory list back would destroy the user's
     * morph library permanently (there is no backup for this file). A missing
     * file is a successful load of nothing: there is nothing to protect and the
     * user must be able to build a library from scratch.</p>
     */
    public boolean load()
    {
        File file = this.file;

        if (file == null || !file.exists())
        {
            return true;
        }

        try
        {
            List<UserCategory> categories = new ArrayList<UserCategory>();
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonArray object = JsonParser.parseString(content).getAsJsonArray();
            int i = 0;

            for (JsonElement entry : object)
            {
                JsonObject cat = entry.getAsJsonObject();
                UserCategory category = new UserCategory(this, cat.get("title").getAsString());

                if (cat.has("hidden") && cat.get("hidden").isJsonPrimitive())
                {
                    category.hidden = cat.get("hidden").getAsBoolean();
                }

                if (cat.has("morphs"))
                {
                    for (JsonElement string : cat.get("morphs").getAsJsonArray())
                    {
                        try
                        {
                            AbstractMorph morph = MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse(string.getAsString()));

                            if (morph != null)
                            {
                                category.add(morph);
                                i ++;
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

                categories.add(category);
            }

            System.out.println("Loading " + categories.size() + " categories with " + i + " morphs!");

            this.global = categories;

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            Metamorph.LOGGER.error(
                "Could not read the morph library at '" + file + "'. Saving is disabled for this session so the "
                + "file is not replaced with an empty list — fix or move it and restart.", e);

            return false;
        }
    }

    public void save()
    {
        if (!this.loaded)
        {
            return;
        }

        JsonArray array = new JsonArray();
        int i = 0;

        for (UserCategory category : this.global)
        {
            JsonObject cat = new JsonObject();
            JsonArray morphs = new JsonArray();

            cat.addProperty("title", category.getTitle());
            cat.addProperty("hidden", category.hidden);
            cat.add("morphs", morphs);

            for (AbstractMorph morph : category.getMorphs())
            {
                if (morph != null)
                {
                    morphs.add(morph.toNBT().toString());

                    i ++;
                }
            }

            array.add(cat);
        }

        System.out.println("Saving " + array.size() + " categories with " + i + " morphs to list.json!");

        try
        {
            if (this.file != null)
            {
                File parent = this.file.getParentFile();

                if (parent != null)
                {
                    parent.mkdirs();
                }

                /* P284: in place + truncating, with no backup, for a file that
                 * is the user's entire morph library. Rotate then write
                 * atomically — the same protection recordings have had since
                 * 2.7.2. */
                PastCopies.rotate(this.file, ".json");
                AtomicWrite.writeString(this.file, JsonUtils.jsonToPretty(array));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
