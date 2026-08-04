package mchorse.blockbuster.utils;

import mchorse.blockbuster.Blockbuster;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resource-pack PNG enumeration (roadmap P88.1) — the source
 * {@link mchorse.blockbuster.utils.mclib.BlockbusterJarTree} is built from.
 *
 * <p>Port of Blockbuster 2.7.2's {@code mchorse.blockbuster.utils
 * .ResourcePackUtils#getAllPictures}. Legacy reflected Forge's
 * {@code FMLClientHandler.resourcePackList}, walked it until it hit the
 * <b>first</b> pack whose {@code getResourceDomains()} contained
 * {@code "blockbuster"}, enumerated that one pack (recursive directory walk for
 * folder packs, {@code ZipFile.entries()} for zip packs, unwrapping
 * {@code LegacyV2Adapter} by reflection) filtering on {@code .png}, and
 * {@code break}ed. All of that machinery — the pack-list reflection, the
 * first-pack-wins {@code break}, the zip walk and its silently swallowed
 * {@code IllegalAccessException} — exists only because 1.12.2 had no public
 * "list everything under a namespace" API.</p>
 *
 * <p>1.20.4 does: {@link ResourceManager#findResources(String,
 * java.util.function.Predicate)} enumerates every namespaced path the stacked
 * packs expose, already deduplicated (the winning pack's resource per id). So the
 * port keeps the <b>result contract</b> — only {@code blockbuster}-namespace
 * {@code .png} locations, sorted by path — and drops the reflection entirely.</p>
 *
 * <h2>P281 — the starting path is not optional</h2>
 *
 * <p>P88.1 shipped this call as {@code findResources("", …)}, an argument no
 * vanilla caller ever passes, and every one of the three pack implementations
 * reads it differently:</p>
 *
 * <ul>
 *   <li>{@code DirectoryResourcePack.findResources} runs
 *       {@code PathUtil.split(prefix)} first and only walks on the {@code Left}
 *       branch; the empty string is a {@code DataResult} error, so the pack
 *       logged {@code Invalid path : Invalid path ''} at <b>ERROR</b> and
 *       enumerated <b>nothing</b>. That is the pair of vanilla errors every
 *       Blockbuster session printed on every resource reload (F3+T included) and
 *       no pre-Blockbuster session did.</li>
 *   <li>{@code ZipResourcePack.findResources} matches on
 *       {@code "assets/<ns>/" + prefix + "/"}, which for the empty prefix is
 *       {@code "assets/blockbuster//"} — a prefix no zip entry can start with, so
 *       a zipped resource pack contributed nothing either. Silently.</li>
 *   <li>Fabric's {@code ModNioResourcePack} resolves the prefix as a path
 *       segment, and {@code nsPath.resolve("")} is {@code nsPath} — which is why
 *       the mod's own 71 PNGs showed up anyway and the defect read as
 *       "two harmless vanilla errors" rather than as broken enumeration.</li>
 * </ul>
 *
 * <p>{@link #PICTURES_PREFIX} is the fix. Every picture the mod ships lives under
 * {@code assets/blockbuster/textures/} (pinned by
 * {@code BlockbusterJarTreeTest.everyShippedPictureLivesUnderTheEnumerationPrefix}),
 * so the narrower walk returns the same identifiers from the mod's own pack while
 * dropping the whole-asset-tree walk of every other pack — and it removes the two
 * ERROR lines by giving the directory packs a path they can actually split.</p>
 *
 * <h2>Pack precedence — the deliberate divergence</h2>
 *
 * <p>Legacy stopped at the <b>first</b> pack declaring the {@code blockbuster}
 * domain. That list was {@code FMLClientHandler.resourcePackList}, which
 * {@code Minecraft.init} passes as {@code defaultResourcePacks} = the vanilla
 * jar pack plus one pack per mod; the user's selected resource packs are a
 * <em>separate</em> list that {@code Minecraft.refreshResources} appends
 * afterwards and that this code never saw. Exactly one entry in it ever declared
 * {@code blockbuster} — Blockbuster's own mod pack — so the {@code break} was an
 * early exit over a single-candidate list, not a precedence policy. There is no
 * legacy rule saying "a resource pack's {@code blockbuster:} PNGs are excluded";
 * there was simply no list in which one could appear.</p>
 *
 * <p>The port therefore keeps the whole stacked view on purpose. It is a superset
 * by construction: the mod pack always supplies the shipped 71, so a player with
 * no Blockbuster-aware resource pack sees exactly the 1.12.2 listing, and a
 * player who installs one gets its additions in the picker (an <i>override</i> of
 * a shipped path changes nothing — {@code findResources} keys by identifier).
 * The alternative — filtering on the winning pack — would be <b>worse</b> parity,
 * not better: it drops any shipped path a resource pack overrides, while 1.12.2,
 * reading the mod pack's own file list, kept it.</p>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/utils/ResourcePackUtils.java</p>
 */
public class ResourcePackUtils
{
    /**
     * The {@code startingPath} handed to {@link ResourceManager#findResources}.
     *
     * <p>Must stay non-empty (see the class note), must not carry a leading or
     * trailing {@code /} (packs append their own separator), and must stay an
     * ancestor of every {@code .png} the mod ships — widen it, do not delete it,
     * if a picture ever lands outside {@code assets/blockbuster/textures/}.</p>
     */
    public static final String PICTURES_PREFIX = "textures";

    /**
     * Collect every {@code .png} in the mod's own namespace, sorted by path
     * (legacy sorted with {@code Comparator.comparing(ResourceLocation::getResourcePath)}
     * in the jar-tree constructor; doing it here keeps the tree builder verbatim).
     *
     * <p>Total-reader: a null manager (headless / pre-init) yields an empty list
     * rather than throwing.</p>
     */
    public static List<ResourceLocation> getAllPictures(ResourceManager manager)
    {
        List<ResourceLocation> locations = new ArrayList<ResourceLocation>();

        if (manager == null)
        {
            return locations;
        }

        try
        {
            for (Identifier id : manager.findResources(PICTURES_PREFIX, ResourcePackUtils::isPicture).keySet())
            {
                locations.add(RLUtils.create(id.getNamespace(), id.getPath()));
            }
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to enumerate the mod's own pictures", e);
        }

        locations.sort(Comparator.comparing(ResourceLocation::getResourcePath));

        return locations;
    }

    private static boolean isPicture(Identifier id)
    {
        return id.getNamespace().equals(Blockbuster.MOD_ID) && id.getPath().endsWith(".png");
    }
}
