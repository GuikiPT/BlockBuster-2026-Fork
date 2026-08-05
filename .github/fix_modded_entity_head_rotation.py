from pathlib import Path

root = Path.cwd()
adapter_path = root / "src/main/java/mchorse/metamorph/compat/EntityMorphAdapter.java"
compat_path = root / "src/main/java/mchorse/metamorph/compat/EntityMorphCompatibility.java"
entity_path = root / "src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java"
renderer_path = root / "src/client/java/mchorse/metamorph/client/render/EntityMorphRenderer.java"
pixelmon_path = root / "src/main/java/mchorse/metamorph/compat/PixelmonEntityMorphAdapter.java"

for path in (adapter_path, compat_path, entity_path, renderer_path):
    if not path.exists():
        raise SystemExit(f"Required generated source is missing: {path}")

adapter = adapter_path.read_text(encoding="utf-8")
head_policy = '''    /**
     * Whether the morph host's vanilla head yaw and pitch should be copied into
     * this entity. Return false for renderers that own head/aim animation and
     * normally keep Minecraft's relative head rotation at zero.
     */
    default boolean mirrorsVanillaHeadRotation(LivingEntity entity)
    {
        return true;
    }

'''
head_anchor = "    /** Mirror a custom animation controller after vanilla state was copied. */\n"
if "mirrorsVanillaHeadRotation" not in adapter:
    if head_anchor not in adapter:
        raise SystemExit("EntityMorphAdapter animation anchor was not found")
    adapter = adapter.replace(head_anchor, head_policy + head_anchor, 1)
adapter_path.write_text(adapter, encoding="utf-8")

compat = compat_path.read_text(encoding="utf-8")
register_anchor = "        register(new CobblemonEntityMorphAdapter());\n"
register_pixelmon = register_anchor + "        register(new PixelmonEntityMorphAdapter());\n"
if "register(new PixelmonEntityMorphAdapter())" not in compat:
    if register_anchor not in compat:
        raise SystemExit("EntityMorphCompatibility built-in adapter anchor was not found")
    compat = compat.replace(register_anchor, register_pixelmon, 1)

adapters_method = '''    public static List<EntityMorphAdapter> adapters()
    {
        return List.copyOf(ADAPTERS);
    }
'''
rotation_bridge = adapters_method + '''
    /**
     * Mirror the host's orientation into a morph entity. Vanilla mobs receive
     * the complete legacy rotation bridge. Adapters may suppress only relative
     * head yaw/pitch while preserving body direction and interpolation.
     */
    public static void mirrorRotations(LivingEntity entity, LivingEntity target)
    {
        if (entity == null || target == null)
        {
            return;
        }

        boolean mirrorHead = true;

        for (EntityMorphAdapter adapter : ADAPTERS)
        {
            try
            {
                if (adapter.supports(entity) && !adapter.mirrorsVanillaHeadRotation(entity))
                {
                    mirrorHead = false;
                    break;
                }
            }
            catch (Throwable throwable)
            {
                Metamorph.LOGGER.warn("Entity morph adapter {} failed to choose a head-rotation policy", adapter.id(), throwable);
            }
        }

        entity.setYaw(target.getYaw());
        entity.setBodyYaw(target.bodyYaw);
        entity.prevYaw = target.prevYaw;
        entity.prevBodyYaw = target.prevBodyYaw;

        if (mirrorHead)
        {
            entity.setPitch(target.getPitch());
            entity.setHeadYaw(target.getHeadYaw());
            entity.prevPitch = target.prevPitch;
            entity.prevHeadYaw = target.prevHeadYaw;
        }
        else
        {
            /* Keep the complete model facing the actor, but do not manufacture
             * a vanilla neck/head offset for animation systems that never use
             * one on their native entities (notably modern Pixelmon). */
            entity.setPitch(0.0F);
            entity.setHeadYaw(entity.bodyYaw);
            entity.prevPitch = 0.0F;
            entity.prevHeadYaw = entity.prevBodyYaw;
        }
    }
'''
if "public static void mirrorRotations(" not in compat:
    if adapters_method not in compat:
        raise SystemExit("EntityMorphCompatibility adapters() anchor was not found")
    compat = compat.replace(adapters_method, rotation_bridge, 1)
compat_path.write_text(compat, encoding="utf-8")

pixelmon_path.write_text(r'''package mchorse.metamorph.compat;

import net.minecraft.entity.LivingEntity;

/**
 * Pixelmon's modern renderer owns Pokémon animation and normally leaves
 * Minecraft's relative head yaw/pitch neutral. Copying an actor/player's
 * vanilla look rotation makes every Pokémon appear to track the camera, which
 * did not happen with Pixelmon's 1.12.2 renderer.
 *
 * <p>No compile-time Pixelmon dependency is used: superclass names are checked
 * so Blockbuster remains loadable when Pixelmon is absent.</p>
 */
public final class PixelmonEntityMorphAdapter implements EntityMorphAdapter
{
    private static final String ENTITY_PACKAGE = "com.pixelmonmod.pixelmon.entities.pixelmon.";
    private static final String BASE_ENTITY = ENTITY_PACKAGE + "AbstractBaseEntity";

    @Override
    public String id()
    {
        return "pixelmon";
    }

    @Override
    public boolean supports(LivingEntity entity)
    {
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass())
        {
            String name = type.getName();

            if (BASE_ENTITY.equals(name) || name.startsWith(ENTITY_PACKAGE))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mirrorsVanillaHeadRotation(LivingEntity entity)
    {
        return false;
    }
}
''', encoding="utf-8")

entity = entity_path.read_text(encoding="utf-8")
old_entity_rotations = '''        this.entity.setYaw(target.getYaw());
        this.entity.setPitch(target.getPitch());
        this.entity.setHeadYaw(target.getHeadYaw());
        this.entity.setBodyYaw(target.bodyYaw);

        this.entity.setVelocity(target.getVelocity());

        this.entity.prevYaw = target.prevYaw;
        this.entity.prevPitch = target.prevPitch;
        this.entity.prevHeadYaw = target.prevHeadYaw;
        this.entity.prevBodyYaw = target.prevBodyYaw;
'''
new_entity_rotations = '''        EntityMorphCompatibility.mirrorRotations(this.entity, target);

        this.entity.setVelocity(target.getVelocity());
'''
if old_entity_rotations in entity:
    entity = entity.replace(old_entity_rotations, new_entity_rotations, 1)
elif new_entity_rotations not in entity:
    raise SystemExit("EntityMorph rotation-copy block was not found")
entity_path.write_text(entity, encoding="utf-8")

renderer = renderer_path.read_text(encoding="utf-8")
compat_import = "import mchorse.metamorph.compat.EntityMorphCompatibility;\n"
renderer_import_anchor = "import mchorse.metamorph.api.morphs.EntityMorph;\n"
if compat_import not in renderer:
    if renderer_import_anchor not in renderer:
        raise SystemExit("EntityMorphRenderer import anchor was not found")
    renderer = renderer.replace(renderer_import_anchor, renderer_import_anchor + compat_import, 1)

old_renderer_method = '''    public static void copyRotations(LivingEntity from, LivingEntity to)
    {
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        to.headYaw = from.headYaw;
        to.bodyYaw = from.bodyYaw;

        to.prevYaw = from.prevYaw;
        to.prevPitch = from.prevPitch;
        to.prevHeadYaw = from.prevHeadYaw;
        to.prevBodyYaw = from.prevBodyYaw;
    }
'''
new_renderer_method = '''    public static void copyRotations(LivingEntity from, LivingEntity to)
    {
        EntityMorphCompatibility.mirrorRotations(to, from);
    }
'''
if old_renderer_method in renderer:
    renderer = renderer.replace(old_renderer_method, new_renderer_method, 1)
elif new_renderer_method not in renderer:
    raise SystemExit("EntityMorphRenderer copyRotations method was not found")
renderer_path.write_text(renderer, encoding="utf-8")

checks = {
    "Pixelmon adapter": pixelmon_path.exists(),
    "adapter policy": "mirrorsVanillaHeadRotation" in adapter_path.read_text(encoding="utf-8"),
    "shared rotation bridge": "mirrorRotations(this.entity, target)" in entity_path.read_text(encoding="utf-8"),
    "renderer bridge": "mirrorRotations(to, from)" in renderer_path.read_text(encoding="utf-8"),
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Head-rotation compatibility patch validation failed: " + ", ".join(failed))

print("Applied adapter-driven head-rotation policy and Pixelmon compatibility.")
