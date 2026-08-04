package mchorse.metamorph.api;

import mchorse.metamorph.api.abilities.IAbility;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.abilities.IAttackAbility;
import mchorse.metamorph.api.creative.MorphList;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.ForeignMorph;
import mchorse.vanilla_pack.MetamorphSection;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Morph manager (roadmap P49).
 *
 * <p>Singleton registry: factory dispatch on the {@code Name} tag, remap,
 * blacklist and active-settings maps. Quirks preserved:</p>
 *
 * <ul>
 *   <li>{@code factories} are iterated in <b>reverse registration order</b>
 *       ({@code for (int i = size-1; i >= 0; i--)}) — later-registered mods
 *       win.</li>
 *   <li>{@code morphFromNBT} <b>mutates the caller's tag</b> (remaps the
 *       {@code Name} in place) — Blockbuster call sites can observe this; kept.</li>
 *   <li>blacklist is a {@link TreeSet} (sorted) — packet ordering follows it.</li>
 *   <li>blacklisted {@code Name}, or a tag with no {@code Name} at all →
 *       {@code null} (total reader: no crash), matching legacy — the
 *       {@code Morph} wrapper just stays empty.</li>
 *   <li>unknown-but-named {@code Name} → a {@link ForeignMorph} placeholder
 *       (roadmap P220) that is inert like legacy's {@code null} but preserves
 *       the foreign compound verbatim; legacy dropped it, which loses data on
 *       the first edit-save cycle of any scene using a morph from a mod that
 *       isn't installed (Emoticons, on this platform, is never installed).</li>
 * </ul>
 *
 * <p>Port note: {@code registerMorphEditors} is client GUI surface and lives
 * with the editor registry; everything else of the legacy creative
 * {@code MorphList} machinery is here — {@link #list}, the {@code UserSection}
 * registration in {@link #register()} and the {@link MetamorphSection}
 * reset/rebuild inside {@link #setActiveBlacklist(World, Set)} (S22 P224).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/MorphManager.java
 */
public class MorphManager
{
    /**
     * Default morph manager
     */
    public static final MorphManager INSTANCE = new MorphManager();

    /**
     * Registered abilities
     */
    public Map<String, IAbility> abilities = new HashMap<String, IAbility>();

    /**
     * Registered actions
     */
    public Map<String, IAction> actions = new HashMap<String, IAction>();

    /**
     * Registered attacks
     */
    public Map<String, IAttackAbility> attacks = new HashMap<String, IAttackAbility>();

    /**
     * Registered morph factories
     */
    public List<IMorphFactory> factories = new ArrayList<IMorphFactory>();

    /**
     * Active morph settings from JSON config
     */
    public Map<String, MorphSettings> activeSettings = new HashMap<String, MorphSettings>();

    /**
     * Active blacklist. Sent either from server, or getting assigned on server
     * start.
     */
    public Set<String> activeBlacklist = new TreeSet<String>();

    /**
     * Active morph ID remapper
     */
    public Map<String, String> activeMap = new HashMap<String, String>();

    /**
     * Foreign morph names already reported by {@link #foreignMorph} — the
     * unknown-morph warning fires once per name, not once per read.
     */
    public final Set<String> reportedForeign = new HashSet<String>();

    /**
     * Global creative morph list (roadmap P57). The bundled {@code UserSection}
     * is registered in {@link #register()}; the entity-catalog
     * {@link MetamorphSection} is added by the vanilla-pack factory's
     * {@code register(manager)} (S22 P224).
     */
    public final MorphList list = new MorphList();

    /**
     * Check whether morph by the given name is blacklisted
     */
    public static boolean isBlacklisted(String name)
    {
        return INSTANCE.activeBlacklist.contains(name);
    }

    /**
     * Set currently used morph ID blacklist
     */
    public void setActiveBlacklist(World world, Set<String> blacklist)
    {
        this.activeBlacklist.clear();
        this.activeBlacklist.addAll(blacklist);

        /* P224: a new blacklist invalidates the entity catalog — legacy rebuilt
         * it immediately (MorphCategory.add drops blacklisted names at insertion
         * time, so the section has to be re-scanned, not filtered). A null world
         * skips the rebuild, exactly as legacy. */
        MetamorphSection section = this.list.getSection(MetamorphSection.class);

        if (section != null && world != null)
        {
            section.reset();
            section.update(world);
        }
    }

    /**
     * Set currently used morph settings
     */
    public void setActiveSettings(Map<String, MorphSettings> settings)
    {
        Map<String, MorphSettings> newSettings = new HashMap<String, MorphSettings>();

        for (Map.Entry<String, MorphSettings> entry : settings.entrySet())
        {
            String key = entry.getKey();
            MorphSettings setting = this.activeSettings.get(key);

            if (setting == null)
            {
                setting = entry.getValue();
            }
            else
            {
                setting.copy(entry.getValue());
            }

            newSettings.put(key, setting);
        }

        this.activeSettings = newSettings;
    }

    /**
     * Set current morph ID remapper
     */
    public void setActiveMap(Map<String, String> map)
    {
        this.activeMap.clear();
        this.activeMap.putAll(map);
    }

    /**
     * That's a singleton, boy!
     */
    private MorphManager()
    {}

    /**
     * Register all morph factories (in reverse registration order)
     */
    public void register()
    {
        this.list.register(new UserSection("user"));

        for (int i = this.factories.size() - 1; i >= 0; i--)
        {
            this.factories.get(i).register(this);
        }
    }

    /**
     * Checks if manager has given morph by ID.
     */
    public boolean hasMorph(String name)
    {
        name = this.remap(name);

        if (isBlacklisted(name))
        {
            return false;
        }

        for (int i = this.factories.size() - 1; i >= 0; i--)
        {
            if (this.factories.get(i).hasMorph(name))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Get an abstract morph from NBT.
     *
     * <p>Iterates factories in reverse order, returning a morph from the first
     * factory that has one. Remaps the tag's {@code Name} <b>in place</b>, and
     * returns {@code null} for blacklisted names and for tags that carry no
     * {@code Name} at all.</p>
     *
     * <p>An unclaimed but named morph yields a {@link ForeignMorph} placeholder
     * (roadmap P220) instead of legacy's {@code null}: inert in every way a
     * {@code null} morph was, but it keeps the foreign compound verbatim so an
     * edit-save cycle doesn't silently delete morphs belonging to a mod that
     * isn't installed. See {@link ForeignMorph} for the full rationale.</p>
     */
    public AbstractMorph morphFromNBT(NbtCompound tag)
    {
        if (tag == null)
        {
            return null;
        }

        if (tag.contains("Name"))
        {
            tag.putString("Name", this.remap(tag.getString("Name")));
        }

        String name = tag.getString("Name");

        if (isBlacklisted(name))
        {
            return null;
        }

        for (int i = this.factories.size() - 1; i >= 0; i--)
        {
            if (this.factories.get(i).hasMorph(name))
            {
                AbstractMorph morph = this.factories.get(i).getMorphFromNBT(tag);

                if (morph != null)
                {
                    this.applySettings(morph);

                    return morph;
                }
            }
        }

        return this.foreignMorph(tag, name);
    }

    /**
     * Build the {@link ForeignMorph} placeholder for a morph name no factory
     * claims (roadmap P220).
     *
     * <p>A tag with no {@code Name} (or an empty one) is not a foreign morph —
     * it's an empty/absent morph slot — and still resolves to {@code null},
     * exactly as legacy. Everything else is preserved verbatim and reported
     * <b>once per name</b> (playback re-reads the same action every frame; a
     * per-call warning would flood the log).</p>
     */
    protected AbstractMorph foreignMorph(NbtCompound tag, String name)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }

        /* With no factory registered at all the manager cannot tell "foreign"
         * from "not initialised yet" — every name would look foreign. Legacy's
         * null is the honest answer there, and it keeps early-boot statics that
         * resolve a morph before registration (e.g. TileEntityModel's
         * DEFAULT_MORPH) behaving exactly as before. */
        if (this.factories.isEmpty())
        {
            return null;
        }

        if (this.reportedForeign.add(name))
        {
            Metamorph.LOGGER.warn("Unknown morph type \"{}\" — no registered factory claims it (is the mod that provides it installed?). Keeping its NBT verbatim so it survives a load/save cycle.", name);
        }

        ForeignMorph morph = new ForeignMorph();

        morph.fromNBT(tag);

        return morph;
    }

    /**
     * Apply morph settings on a given morph
     */
    public void applySettings(AbstractMorph morph)
    {
        morph.setActiveSettings(this.activeSettings.get(morph.name));
    }

    /**
     * Get morph name from the entity
     */
    public String morphNameFromEntity(Entity entity)
    {
        return Registries.ENTITY_TYPE.getId(entity.getType()).toString();
    }

    /**
     * Remap morph name
     */
    public String remap(String name)
    {
        String remapped = this.activeMap.get(name);

        return remapped == null ? name : remapped;
    }
}
