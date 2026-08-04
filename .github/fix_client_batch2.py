from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    file = ROOT / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(text)


def replace(path: str, old: str, new: str, required: bool = False) -> None:
    text = read(path)
    if old not in text:
        if required:
            raise RuntimeError(f'Expected text not found in {path}: {old[:140]!r}')
        return
    write(path, text.replace(old, new))


def add_import(path: str, statement: str) -> None:
    text = read(path)
    if statement in text:
        return
    lines = text.splitlines(True)
    imports = [i for i, line in enumerate(lines) if line.startswith('import ')]
    lines.insert((imports[-1] + 1) if imports else 1, statement)
    write(path, ''.join(lines))


# ---------------------------------------------------------------------------
# JOML model-view stack (RenderSystem no longer returns Minecraft MatrixStack)
# ---------------------------------------------------------------------------
path = 'src/client/java/mchorse/blockbuster/client/gui/dashboard/panels/model_block/GuiModelBlockPanel.java'
add_import(path, 'import org.joml.Matrix4fStack;\n')
replace(path, 'MatrixStack modelView = RenderSystem.getModelViewStack();',
        'Matrix4fStack modelView = RenderSystem.getModelViewStack();')
replace(path, '        modelView.push();\n\n        if (this.model != null)\n        {\n            ModelBlockTransform.apply(modelView, this.model.getSettings());\n        }',
        '        modelView.pushMatrix();\n\n        if (this.model != null)\n        {\n            MatrixStack transform = new MatrixStack();\n            ModelBlockTransform.apply(transform, this.model.getSettings());\n            modelView.mul(transform.peek().getPositionMatrix());\n        }')
replace(path, 'RenderSystem.getModelViewStack().pop();', 'RenderSystem.getModelViewStack().popMatrix();')

path = 'src/client/java/mchorse/mclib/client/gui/framework/elements/GuiModelRenderer.java'
add_import(path, 'import org.joml.Matrix4fStack;\n')
replace(path, 'MatrixStack modelView = RenderSystem.getModelViewStack();',
        'Matrix4fStack modelView = RenderSystem.getModelViewStack();')
replace(path, 'modelView.push();', 'modelView.pushMatrix();')
replace(path, 'modelView.loadIdentity();', 'modelView.identity();')
replace(path, 'modelView.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.pitch));',
        'modelView.rotate(RotationAxis.POSITIVE_X.rotationDegrees(this.pitch));')
replace(path, 'modelView.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.yaw));',
        'modelView.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(this.yaw));')
replace(path, 'modelView.pop();', 'modelView.popMatrix();')

path = 'src/client/java/mchorse/metamorph/client/gui/creative/GuiCreativeMorphsList.java'
add_import(path, 'import org.joml.Matrix4fStack;\n')
replace(path, 'MatrixStack modelView = RenderSystem.getModelViewStack();',
        'Matrix4fStack modelView = RenderSystem.getModelViewStack();')
replace(path, 'modelView.push();', 'modelView.pushMatrix();')
replace(path, 'modelView.pop();', 'modelView.popMatrix();')

path = 'src/client/java/mchorse/blockbuster/mixin/client/WorldRendererParticlesMixin.java'
add_import(path, 'import org.joml.Matrix4fStack;\n')
replace(path, 'MatrixStack modelView = RenderSystem.getModelViewStack();',
        'Matrix4fStack modelView = RenderSystem.getModelViewStack();')
replace(path, 'modelView.push();', 'modelView.pushMatrix();')
replace(path, 'modelView.multiplyPositionMatrix(matrices.peek().getPositionMatrix());',
        'modelView.mul(matrices.peek().getPositionMatrix());')
replace(path, 'modelView.pop();', 'modelView.popMatrix();')


# ---------------------------------------------------------------------------
# VertexConsumer 1.21 interface
# ---------------------------------------------------------------------------
write('src/client/java/mchorse/metamorph/bodypart/DiscardingVertexConsumer.java', r'''package mchorse.metamorph.bodypart;

import net.minecraft.client.render.VertexConsumer;

/** A {@link VertexConsumer} sink used by the body-part matrix recording pass. */
public final class DiscardingVertexConsumer implements VertexConsumer
{
    public static final DiscardingVertexConsumer INSTANCE = new DiscardingVertexConsumer();

    private DiscardingVertexConsumer()
    {}

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this;
    }
}
''')

write('src/client/java/mchorse/blockbuster_pack/morphs/structure/MatrixVertexConsumer.java', r'''package mchorse.blockbuster_pack.morphs.structure;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/** Vertex consumer that applies a captured pose before delegating. */
@Environment(EnvType.CLIENT)
public class MatrixVertexConsumer implements VertexConsumer
{
    private final VertexConsumer delegate;
    private final MatrixStack.Entry entry;

    public MatrixVertexConsumer(VertexConsumer delegate, MatrixStack.Entry entry)
    {
        this.delegate = delegate;
        this.entry = entry;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        this.delegate.vertex(this.entry.getPositionMatrix(), x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        this.delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.delegate.normal(this.entry, x, y, z);
        return this;
    }
}
''')

# Remaining matrix-based normal overloads.
for path in [
    'src/client/java/mchorse/blockbuster/client/RenderingHandler.java',
    'src/client/java/mchorse/blockbuster/client/particles/emitter/BedrockParticle.java',
]:
    text = read(path)
    text = text.replace('.normal(matrices.peek().getNormalMatrix(),', '.normal(matrices.peek(),')
    text = text.replace('.normal(entry.getNormalMatrix(),', '.normal(entry,')
    write(path, text)


# ---------------------------------------------------------------------------
# Resource-pack identity record
# ---------------------------------------------------------------------------
path = 'src/client/java/mchorse/blockbuster/client/ActorsResourcePack.java'
add_import(path, 'import net.minecraft.resource.ResourcePackInfo;\n')
add_import(path, 'import net.minecraft.resource.ResourcePackSource;\n')
add_import(path, 'import net.minecraft.text.Text;\n')
add_import(path, 'import java.util.Optional;\n')
text = read(path)
text = re.sub(
    r'\n\s*@Override\n\s*public String getName\(\)\n\s*\{\n\s*return "Blockbuster\'s Actor Pack";\n\s*\}',
    '\n    @Override\n    public ResourcePackInfo getInfo()\n    {\n        return new ResourcePackInfo("blockbuster_actor_pack", Text.literal("Blockbuster\'s Actor Pack"), ResourcePackSource.NONE, Optional.empty());\n    }',
    text,
)
write(path, text)


# ---------------------------------------------------------------------------
# Modern skull profile component
# ---------------------------------------------------------------------------
path = 'src/client/java/mchorse/blockbuster_pack/client/render/layers/LayerCustomHead.java'
add_import(path, 'import mchorse.mclib.utils.GameProfileNbtUtils;\n')
add_import(path, 'import net.minecraft.component.DataComponentTypes;\n')
add_import(path, 'import net.minecraft.component.type.ProfileComponent;\n')
text = read(path)
text = text.replace('import net.minecraft.nbt.NbtHelper;\n', '')
text = text.replace('import net.minecraft.block.entity.SkullBlockEntity;\n', '')
text = text.replace('RenderLayer renderLayer = SkullBlockEntityRenderer.getRenderLayer(type, profile);',
                    'ProfileComponent profileComponent = stack.get(DataComponentTypes.PROFILE);\n                RenderLayer renderLayer = SkullBlockEntityRenderer.getRenderLayer(type, profileComponent);')
start = text.index('    public GameProfile resolveSkullProfile(ItemStack stack)')
end = text.index('\n    /**', start)
replacement = '''    public GameProfile resolveSkullProfile(ItemStack stack)\n    {\n        ProfileComponent component = stack.get(DataComponentTypes.PROFILE);\n\n        if (component == null)\n        {\n            return null;\n        }\n\n        if (!component.isCompleted())\n        {\n            component.getFuture().thenAccept(resolved -> stack.set(DataComponentTypes.PROFILE, resolved));\n        }\n\n        return component.gameProfile();\n    }\n'''
text = text[:start] + replacement + text[end:]
# Replace legacy NBT helper calls while preserving the headless helper API.
text = text.replace('NbtHelper.toGameProfile(nbt.getCompound(SkullBlockEntity.SKULL_OWNER_KEY))',
                    'GameProfileNbtUtils.read(nbt.getCompound("SkullOwner"))')
text = text.replace('NbtHelper.toGameProfile(nbt.getCompound("SkullOwner"))',
                    'GameProfileNbtUtils.read(nbt.getCompound("SkullOwner"))')
text = text.replace('NbtHelper.writeGameProfile(new NbtCompound(), profile)',
                    'GameProfileNbtUtils.write(new NbtCompound(), profile)')
# Replace the old resolver body with ProfileComponent's async completion path.
resolver_start = text.index('    public static GameProfile resolveProfile(GameProfile profile)')
resolver_end = text.index('\n    /**', resolver_start)
resolver = '''    public static GameProfile resolveProfile(GameProfile profile)\n    {\n        if (profile == null)\n        {\n            return null;\n        }\n\n        ProfileComponent component = new ProfileComponent(profile);\n        ProfileComponent resolved = component.getFuture().getNow(component);\n\n        return resolved.gameProfile();\n    }\n'''
text = text[:resolver_start] + resolver + text[resolver_end:]
write(path, text)


# ---------------------------------------------------------------------------
# Smaller signature/API changes
# ---------------------------------------------------------------------------
path = 'src/client/java/mchorse/blockbuster/client/render/GunMiscRender.java'
add_import(path, 'import net.minecraft.client.render.RenderTickCounter;\n')
replace(path, 'private void onHud(DrawContext context, float tickDelta)',
        'private void onHud(DrawContext context, RenderTickCounter tickCounter)')
replace(path, 'this.handleZoom(player, tickDelta);',
        'this.handleZoom(player, tickCounter.getTickDelta(false));')

path = 'src/client/java/mchorse/mclib/client/gui/framework/elements/utils/GuiInventoryElement.java'
text = read(path)
text = text.replace('return mc.getSearchProvider(SearchManager.ITEM_TOOLTIP).findAll(query);',
                    'return mc.getNetworkHandler() == null\n                    ? Collections.emptyList()\n                    : mc.getNetworkHandler().getSearchManager().getItemTooltipReloadFuture().findAll(query);')
write(path, text)

path = 'src/client/java/mchorse/chameleon/client/render/ChameleonMorphRenderer.java'
text = read(path)
text = re.sub(
    r'RenderSystem\.setupLevelDiffuseLighting\((GUI_LIGHT_0), (GUI_LIGHT_1),\s*[^;]+\);',
    r'RenderSystem.setupLevelDiffuseLighting(\1, \2);',
    text,
)
write(path, text)

path = 'src/client/java/mchorse/mclib/client/gui/framework/elements/keyframes/GuiKeyframeElement.java'
replace(path, 'protected static void flush()', 'protected static void flush(BufferBuilder buffer)')

replace('src/client/java/mchorse/blockbuster/client/render/tileentity/TileEntityModelItemStackRenderer.java',
        'mc.getTickDelta()', 'mc.getRenderTickCounter().getTickDelta(false)')
replace('src/client/java/mchorse/blockbuster/client/render/RenderCustomModel.java',
        'entity.getRoll() + partialTicks', 'entity.getGlidingTicks() + partialTicks')
replace('src/client/java/mchorse/blockbuster/client/render/PoseContexts.java',
        'context.ticksElytraFlying = entity.getRoll();',
        'context.ticksElytraFlying = entity.getGlidingTicks();')

# Packed ARGB replaced the four float color arguments on ModelPart.render.
path = 'src/client/java/mchorse/blockbuster_pack/client/render/layers/LayerActorArmor.java'
text = read(path)
text = re.sub(r'\n\s*float r = \(color >> 16 & 255\) / 255F;\n\s*float g = \(color >> 8 & 255\) / 255F;\n\s*float b = \(color & 255\) / 255F;', '', text)
text = text.replace('part.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, r, g, b, 1F);',
                    'part.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 0xFF000000 | color);')
text = re.sub(r'part\.render\((matrices, [^,]+, light, OverlayTexture\.DEFAULT_UV), 1F, 1F, 1F, 1F\);',
              r'part.render(\1, 0xFFFFFFFF);', text)
write(path, text)

path = 'src/client/java/mchorse/blockbuster_pack/client/render/layers/LayerElytra.java'
text = read(path)
text = re.sub(r'this\.modelElytra\.render\((matrices, [^,]+, light, OverlayTexture\.DEFAULT_UV), 1F, 1F, 1F, 1F\);',
              r'this.modelElytra.render(\1, 0xFFFFFFFF);', text)
write(path, text)

# Public registry-aware wrapper for the item renderer.
path = 'src/main/java/mchorse/blockbuster/common/tileentity/TileEntityModel.java'
text = read(path)
if 'public void readModelNbt(' not in text:
    marker = '    @Override\n    protected void readNbt(NbtCompound compound, RegistryWrapper.WrapperLookup registries)'
    wrapper = '''    public void readModelNbt(NbtCompound compound, RegistryWrapper.WrapperLookup registries)\n    {\n        this.readNbt(compound, registries);\n    }\n\n'''
    text = text.replace(marker, wrapper + marker)
write(path, text)
replace('src/client/java/mchorse/blockbuster/client/render/tileentity/TileEntityModelItemStackRenderer.java',
        'te.readNbt(tag, RegistryUtils.getLookup());',
        'te.readModelNbt(tag, RegistryUtils.getLookup());')

path = 'src/client/java/mchorse/blockbuster_pack/morphs/structure/StructureRenderer.java'
add_import(path, 'import mchorse.mclib.utils.RegistryUtils;\n')
replace(path, 'BlockEntity.createFromNbt(pos, state, entry.getValue())',
        'BlockEntity.createFromNbt(pos, state, entry.getValue(), RegistryUtils.getLookup())')

print('Applied Minecraft 1.21.1 client migration batch 2.')
