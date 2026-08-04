from pathlib import Path

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text)


def replace(path: str, old: str, new: str, required: bool = True) -> None:
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


# The MatrixStack.Entry is already available at both call sites; pass it to
# VertexConsumer.normal instead of the removed Matrix3f overload.
for path in [
    'src/client/java/mchorse/chameleon/metamorph/editor/render/ChameleonStencilRenderer.java',
    'src/client/java/mchorse/chameleon/lib/render/ChameleonCubeRenderer.java',
]:
    replace(path, '.normal(normalMatrix,', '.normal(entry,')

# Batch 2 changed the method parameter to `buffer`; use that name in the body.
replace(
    'src/client/java/mchorse/mclib/client/gui/framework/elements/keyframes/GuiKeyframeElement.java',
    'BufferRenderer.drawWithGlobalProgram(builder.end());',
    'BufferRenderer.drawWithGlobalProgram(buffer.end());',
)

# 1.21 ModelPart.render accepts packed ARGB. Keep the custom Elytra model API
# aligned with vanilla instead of unpacking/repacking at every call site.
path = 'src/client/java/mchorse/blockbuster_pack/client/model/ModelElytra.java'
replace(path,
        'public void render(MatrixStack matrices, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a)',
        'public void render(MatrixStack matrices, VertexConsumer consumer, int light, int overlay, int color)')
replace(path,
        'this.leftWing.render(matrices, consumer, light, overlay, r, g, b, a);',
        'this.leftWing.render(matrices, consumer, light, overlay, color);')
replace(path,
        'this.rightWing.render(matrices, consumer, light, overlay, r, g, b, a);',
        'this.rightWing.render(matrices, consumer, light, overlay, color);')

# Preserve the exact vanilla Elytra animation counter through the existing
# required LivingEntity accessor mixin. The field is protected in 1.21.1.
path = 'src/main/java/mchorse/blockbuster/mixin/LivingEntityAccessor.java'
add_import(path, 'import org.spongepowered.asm.mixin.gen.Accessor;\n')
text = read(path)
if 'metamorph$getFallFlyingTicks' not in text:
    marker = 'public interface LivingEntityAccessor\n{\n'
    text = text.replace(marker, marker + '    @Accessor("fallFlyingTicks")\n    int metamorph$getFallFlyingTicks();\n\n')
write(path, text)

for path in [
    'src/client/java/mchorse/blockbuster/client/render/RenderCustomModel.java',
    'src/client/java/mchorse/blockbuster/client/render/PoseContexts.java',
]:
    add_import(path, 'import mchorse.blockbuster.mixin.LivingEntityAccessor;\n')

replace(
    'src/client/java/mchorse/blockbuster/client/render/RenderCustomModel.java',
    'entity.getGlidingTicks() + partialTicks',
    '((LivingEntityAccessor) entity).metamorph$getFallFlyingTicks() + partialTicks',
)
replace(
    'src/client/java/mchorse/blockbuster/client/render/PoseContexts.java',
    'context.ticksElytraFlying = entity.getGlidingTicks();',
    'context.ticksElytraFlying = ((LivingEntityAccessor) entity).metamorph$getFallFlyingTicks();',
)

print('Applied Minecraft 1.21.1 client migration batch 3.')
