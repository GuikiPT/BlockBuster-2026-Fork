package mchorse.vanilla_pack;

import java.util.Map;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.metamorph.api.IMorphFactory;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.abilities.IAbility;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.abilities.IAttackAbility;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.vanilla_pack.abilities.Climb;
import mchorse.vanilla_pack.abilities.FireProof;
import mchorse.vanilla_pack.abilities.Fly;
import mchorse.vanilla_pack.abilities.Glide;
import mchorse.vanilla_pack.abilities.Hungerless;
import mchorse.vanilla_pack.abilities.Jumping;
import mchorse.vanilla_pack.abilities.NightVision;
import mchorse.vanilla_pack.abilities.PreventFall;
import mchorse.vanilla_pack.abilities.Rotten;
import mchorse.vanilla_pack.abilities.SnowWalk;
import mchorse.vanilla_pack.abilities.StepUp;
import mchorse.vanilla_pack.abilities.SunAllergy;
import mchorse.vanilla_pack.abilities.Swim;
import mchorse.vanilla_pack.abilities.WaterAllergy;
import mchorse.vanilla_pack.abilities.WaterBreath;
import mchorse.vanilla_pack.actions.Endermite;
import mchorse.vanilla_pack.actions.Explode;
import mchorse.vanilla_pack.actions.FireBreath;
import mchorse.vanilla_pack.actions.Fireball;
import mchorse.vanilla_pack.actions.Jump;
import mchorse.vanilla_pack.actions.Potions;
import mchorse.vanilla_pack.actions.ShulkerBullet;
import mchorse.vanilla_pack.actions.Sliverfish;
import mchorse.vanilla_pack.actions.SmallFireball;
import mchorse.vanilla_pack.actions.Snowball;
import mchorse.vanilla_pack.actions.Spit;
import mchorse.vanilla_pack.actions.Teleport;
import mchorse.vanilla_pack.attacks.KnockbackAttack;
import mchorse.vanilla_pack.attacks.MobAttack;
import mchorse.vanilla_pack.attacks.PoisonAttack;
import mchorse.vanilla_pack.attacks.WitherAttack;
import mchorse.vanilla_pack.morphs.BlockMorph;
import mchorse.vanilla_pack.morphs.IronGolemMorph;
import mchorse.vanilla_pack.morphs.ItemMorph;
import mchorse.vanilla_pack.morphs.LabelMorph;
import mchorse.vanilla_pack.morphs.ShulkerMorph;
import mchorse.vanilla_pack.morphs.UndeadMorph;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Metamorph factory — the vanilla-pack {@link IMorphFactory} (roadmap P53.1).
 *
 * <p>Underlying morph factory: {@code Name}-based construction of the reserved
 * non-entity morphs ({@code player}/{@code block}/{@code item}/{@code label})
 * and per-entity subclasses ({@link UndeadMorph}, {@link IronGolemMorph},
 * {@link ShulkerMorph}); everything else that resolves to a living
 * {@link EntityType} becomes a plain {@link EntityMorph}.</p>
 *
 * <p>Quirks preserved: {@code "player"} matches {@code equalsIgnoreCase}, the
 * other reserved names are exact; the legacy 1.12 id {@code minecraft:villager_golem}
 * <b>and</b> the modern {@code minecraft:iron_golem} both dispatch to
 * {@link IronGolemMorph}. <b>Changed:</b> a player morph with a missing profile
 * used to return {@code null} — it is a plain {@link EntityMorph} named
 * {@link EntityMorph#PLAYER_ID} now and falls back to
 * {@link EntityMorph#DEFAULT_PLAYER_PROFILE}, so it draws a default skin
 * instead of disappearing. "Is a living entity" is detected headlessly via
 * {@link DefaultAttributeRegistry#hasDefinitionFor(EntityType)} — every
 * {@code LivingEntity} type registers default attributes, non-living ones don't
 * — replacing the legacy {@code EntityLivingBase.isAssignableFrom} class check.</p>
 *
 * <p>{@code register} seeds the 15 vanilla ability ids, 12 action ids and 4
 * attack ids into {@link MorphManager#abilities}/{@code actions}/{@code attacks}
 * (roadmap P49.1) in legacy's exact order. <b>Those id strings are a disk
 * contract</b> — {@code assets/metamorph/morphs.json}, every user-edited
 * {@code config/metamorph/morphs.json} and every serialized
 * {@code MorphSettings} reference them by name. Note the deliberate
 * id/class mismatch {@code "silverfish"} → {@code Sliverfish} (legacy typo in
 * the class name, kept for diff-ability). It also registers the creative
 * {@link MetamorphSection} (S22 P224) — the vanilla-entity catalog. The client
 * {@code registerMorphEditors} ordering ({@code GuiLabelMorph,
 * GuiItemMorph, GuiBlockMorph, GuiPlayerMorph, GuiEntityMorph} — subclass
 * editors first so their {@code canEdit} wins) is P59.2 client surface.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/MetamorphFactory.java
 */
public class MetamorphFactory implements IMorphFactory
{
    @Override
    public void register(MorphManager manager)
    {
        /* Define shortcuts */
        Map<String, IAbility> abilities = manager.abilities;
        Map<String, IAttackAbility> attacks = manager.attacks;
        Map<String, IAction> actions = manager.actions;

        /* Register default abilities */
        abilities.put("climb", new Climb());
        abilities.put("fire_proof", new FireProof());
        abilities.put("fly", new Fly());
        abilities.put("glide", new Glide());
        abilities.put("hungerless", new Hungerless());
        abilities.put("jumping", new Jumping());
        abilities.put("night_vision", new NightVision());
        abilities.put("prevent_fall", new PreventFall());
        abilities.put("rotten", new Rotten());
        abilities.put("snow_walk", new SnowWalk());
        abilities.put("step_up", new StepUp());
        abilities.put("sun_allergy", new SunAllergy());
        abilities.put("swim", new Swim());
        abilities.put("water_allergy", new WaterAllergy());
        abilities.put("water_breath", new WaterBreath());

        /* Register default actions */
        actions.put("endermite", new Endermite());
        actions.put("explode", new Explode());
        actions.put("fireball", new Fireball());
        actions.put("fire_breath", new FireBreath());
        actions.put("jump", new Jump());
        actions.put("potions", new Potions());
        actions.put("shulker_bullet", new ShulkerBullet());
        actions.put("silverfish", new Sliverfish());
        actions.put("small_fireball", new SmallFireball());
        actions.put("snowball", new Snowball());
        actions.put("spit", new Spit());
        actions.put("teleport", new Teleport());

        /* Register default attacks */
        attacks.put("knockback", new KnockbackAttack());
        attacks.put("mob", new MobAttack());
        attacks.put("poison", new PoisonAttack());
        attacks.put("wither", new WitherAttack());

        /* Register main section (P224): the vanilla-entity creative catalog.
         * Legacy title "entity" — it keys the morph.section.entity translation. */
        manager.list.register(new MetamorphSection(this, "entity"));
    }

    /**
     * P53.1 + P71: built-in legacy 1.12 entity-id aliasing (e.g.
     * {@code villager_golem → iron_golem}, {@code zombie_pigman →
     * zombified_piglin}) applied <b>before</b> registry lookups, so a legacy
     * scene/record morph dispatches through the normal
     * {@code MorphManager.hasMorph} gate instead of dying there. Returns the
     * input unchanged when it already resolves (or is unknown — total).
     */
    public static String aliasEntityId(String name)
    {
        Identifier key = Identifier.tryParse(name);

        if (key != null && Registries.ENTITY_TYPE.containsId(key))
        {
            return name;
        }

        String modern = LegacyIdMap.entityId(name);

        if (modern != null)
        {
            Identifier modernKey = Identifier.tryParse(modern);

            if (modernKey != null && Registries.ENTITY_TYPE.containsId(modernKey))
            {
                return modern;
            }
        }

        return name;
    }

    /**
     * Whether this factory can produce a morph by the given name.
     */
    @Override
    public boolean hasMorph(String name)
    {
        if (name.equalsIgnoreCase("player"))
        {
            return true;
        }

        if (name.equals("block") || name.equals("item") || name.equals("label"))
        {
            return true;
        }

        name = aliasEntityId(name);

        Identifier key = Identifier.tryParse(name);

        if (key == null || !Registries.ENTITY_TYPE.containsId(key))
        {
            return false;
        }

        EntityType<?> type = Registries.ENTITY_TYPE.get(key);

        return DefaultAttributeRegistry.hasDefinitionFor(type);
    }

    /**
     * Create a morph from NBT (name dispatch).
     */
    @Override
    public AbstractMorph getMorphFromNBT(NbtCompound tag)
    {
        String name = tag.getString("Name");
        AbstractMorph morph;

        /* A player disguise is a plain entity morph named minecraft:player now
         * (BBS's MobForm shape); EntityMorph.fromNBT rewrites the legacy bare
         * "player" name, so the branch only has to pick the class. Unlike the
         * old PlayerMorph path this no longer rejects a morph whose username
         * never resolved — it falls back to EntityMorph.DEFAULT_PLAYER_PROFILE
         * and draws a default skin instead of vanishing. */
        if (name.equalsIgnoreCase("player") || name.equals(EntityMorph.PLAYER_ID))
        {
            EntityMorph player = new EntityMorph();

            player.fromNBT(tag);

            return player;
        }

        if (name.equals("block"))
        {
            morph = new BlockMorph();
        }
        else if (name.equals("item"))
        {
            morph = new ItemMorph();
        }
        else if (name.equals("label"))
        {
            morph = new LabelMorph();
        }
        else
        {
            /* Subclass dispatch keys on the legacy id too (villager_golem
             * branch), then the tag's Name maps forward to the modern id so
             * EntityMorph.setupEntity resolves against the 1.20.4 registry —
             * the P53.1 "dispatch from legacy NBT, map forward" contract.
             * (morphFromNBT already mutates the caller's tag for remaps.) */
            morph = morphFromName(name);

            String aliased = aliasEntityId(name);

            if (!aliased.equals(name))
            {
                tag.putString("Name", aliased);
            }
        }

        morph.fromNBT(tag);

        return morph;
    }

    /**
     * Get an entity morph subclass from a name (legacy + modern ids).
     */
    public EntityMorph morphFromName(String name)
    {
        if (name.equals("minecraft:zombie") || name.equals("minecraft:skeleton") || name.equals("minecraft:zombie_villager"))
        {
            return new UndeadMorph();
        }
        else if (name.equals("minecraft:villager_golem") || name.equals("minecraft:iron_golem"))
        {
            return new IronGolemMorph();
        }
        else if (name.equals("minecraft:shulker"))
        {
            return new ShulkerMorph();
        }

        return new EntityMorph();
    }
}
