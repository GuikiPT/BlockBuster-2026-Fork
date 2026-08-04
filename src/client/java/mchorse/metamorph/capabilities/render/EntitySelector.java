package mchorse.metamorph.capabilities.render;

import mchorse.blockbuster.legacy.LegacyIdMap;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Entity selector (roadmap P54.1).
 *
 * <p>Client-side rule that matches a living entity by display {@code name} and
 * registry {@code type} (with optional {@code "!"} negation and {@code "*"}
 * wildcard) and, when a {@code match} compound is present, shallow per-key NBT
 * equality against the entity's serialized NBT. When matched, {@link
 * ModelRenderer} substitutes the selector's {@code morph} for the entity's
 * vanilla model.</p>
 *
 * <p>Load-bearing 1.12.2 quirks preserved verbatim:</p>
 * <ul>
 *   <li>empty {@code name} matches any entity; empty {@code type} <b>never</b>
 *       matches (the user must type {@code "*"}),</li>
 *   <li>a leading {@code "!"} negates via XOR ({@code negative != equals}),</li>
 *   <li>players whose registry key is absent map to type {@code "player"},</li>
 *   <li>an entity with no registry key gets the empty-string id,</li>
 *   <li>{@code match} is a shallow primitive/string subset compare against
 *       {@code entity.writeToNBT}.</li>
 * </ul>
 *
 * <p><b>Pre-flattening {@code type} ids (P54.1).</b> 1.12.2 compared the typed
 * string against {@code EntityList.getKey(entity)}, so a selector saved back
 * then holds a 1.12 registry name ({@code EntityHorse}, {@code zombie_pigman},
 * {@code villager_golem}, {@code xp_orb}, …) that no longer exists in 1.20.4.
 * {@link #resolveType(String)} keeps 1.12's check <b>first</b> — the id exactly
 * as written, if it is registered right now (which covers every modern id and,
 * crucially, every <i>modded</i> id a user types today) — and only then routes
 * the string through the central P71 id-translation shim. Resolution happens
 * silently at match time: the user's typed text is never rewritten, so the GUI
 * box, the JSON on disk, and the {@code "!"}/{@code "*"} semantics are all
 * untouched.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/capabilities/render/EntitySelector.java
 */
public class EntitySelector
{
    public boolean enabled = true;
    public String name = "";
    public String type = "";
    public NbtCompound match;
    public NbtCompound morph;

    /**
     * Match a live entity. Total: never throws (delegates to the pure
     * {@link #matches(String, Identifier, Supplier)} core).
     */
    public boolean matches(LivingEntity target)
    {
        if (target == null || !this.enabled)
        {
            return false;
        }

        String nameTag = target.getName().getString();
        Identifier entityName = Registries.ENTITY_TYPE.getId(target.getType());

        if (entityName == null)
        {
            /* Legacy: EntityList.getKey(target) == null → "player" for players,
             * "" otherwise. In 1.20.4 the player type is always registered as
             * minecraft:player, but keep the fallback shape verbatim. */
            entityName = target instanceof PlayerEntity
                ? new Identifier("player")
                : Identifier.tryParse("");
        }

        return this.matches(nameTag, entityName, () -> target.writeNbt(new NbtCompound()));
    }

    /**
     * Pure matching core, extracted for headless testing. The NBT supplier is
     * only invoked when a non-empty {@code match} compound must be compared —
     * exactly like legacy, which only called {@code writeToNBT} on match.
     */
    public boolean matches(String nameTag, Identifier entityName, Supplier<NbtCompound> entityTag)
    {
        if (nameTag == null)
        {
            nameTag = "";
        }

        String n = this.name;
        String t = this.type;

        boolean negativeName = n.startsWith("!");
        boolean negativeType = t.startsWith("!");

        if (negativeName) n = n.substring(1);
        if (negativeType) t = t.substring(1);

        /* Legacy used new ResourceLocation(t); resolveType is total (null on the
         * empty/invalid string) and never matches a real entity id, which
         * preserves "empty type never matches". */
        Identifier rt = resolveType(t);

        boolean matchesName = this.name.isEmpty() || negativeName != nameTag.equals(n);
        boolean matchesType = this.type.equals("*") || negativeType != (entityName != null && entityName.equals(rt));

        if (matchesName && matchesType)
        {
            if (this.match != null && !this.match.isEmpty())
            {
                return this.match(entityTag.get());
            }

            return true;
        }

        return false;
    }

    /**
     * Resolve the selector's {@code type} text (already stripped of a leading
     * {@code "!"}) to the entity-type id it should be compared against.
     *
     * <p>Registry-first, then the P71 shim — mirroring
     * {@code mchorse.aperture.utils.EntitySelector#resolveEntityType}:</p>
     * <ol>
     *   <li>the id exactly as typed, when it is registered right now: this is
     *       1.12's {@code EntityList.isRegistered(new ResourceLocation(s))} and
     *       it is what keeps <b>modded</b> selectors that work today working —
     *       shim-first would rewrite them,</li>
     *   <li>otherwise the P71 legacy-id translation
     *       ({@code zombie_pigman} → {@code minecraft:zombified_piglin},
     *       {@code EntityHorse} → {@code minecraft:horse}, …),</li>
     *   <li>otherwise the parsed id unchanged — the pre-P54.1 behaviour, so an
     *       unregistered, unmappable id still simply fails to match instead of
     *       becoming a wildcard or a crash.</li>
     * </ol>
     *
     * <p>Total by contract: never throws. {@code null} for an unparseable
     * string ({@link Identifier#tryParse} is the total form of 1.12's
     * {@code ResourceLocation} constructor) and the empty-path id
     * {@code minecraft:} for {@code ""} — neither can ever equal a live
     * entity's id, which is exactly what makes the "empty {@code type} never
     * matches" quirk hold. The empty string is short-circuited before the shim
     * so a freshly created selector never emits a P71 "no mapping" warning.</p>
     *
     * <p>Note the shim is also the only path that can handle a pre-1.11
     * CamelCase id: {@code Identifier.tryParse("EntityHorse")} is {@code null}
     * (uppercase is invalid in a 1.20.4 path), so step 1 cannot see it and step
     * 2 lowercases it through {@code LegacyIdMap}.</p>
     */
    public static Identifier resolveType(String type)
    {
        if (type == null)
        {
            /* Identifier.tryParse(null) NPEs — it is only total for bad text. */
            return null;
        }

        /* Legacy step: the id as written, if it is registered right now. */
        Identifier direct = Identifier.tryParse(type);

        if (direct != null && Registries.ENTITY_TYPE.containsId(direct))
        {
            return direct;
        }

        /* P71: pre-flattening entity id saved in a 1.12.2 selector. */
        if (!type.trim().isEmpty())
        {
            EntityType<?> mapped = LegacyIdMap.entityType(type).orElse(null);

            if (mapped != null)
            {
                return Registries.ENTITY_TYPE.getId(mapped);
            }
        }

        return direct;
    }

    private boolean match(NbtCompound entityTag)
    {
        if (entityTag == null)
        {
            return false;
        }

        for (String key : this.match.getKeys())
        {
            NbtElement value = this.match.get(key);
            NbtElement entityValue = entityTag.get(key);

            if (value == null || !value.equals(entityValue))
            {
                return false;
            }
        }

        return true;
    }
}
