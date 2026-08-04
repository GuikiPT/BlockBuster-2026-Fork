from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text)


def replace(path: str, old: str, new: str, required: bool = True) -> None:
    text = read(path)
    if old not in text:
        if required:
            raise RuntimeError(f'Expected text not found in {path}: {old[:160]!r}')
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


# Morph particle render components never write billboard vertices. The legacy
# parameter remains for interface compatibility, so initialise it explicitly.
path = 'src/client/java/mchorse/blockbuster/client/particles/emitter/BedrockEmitter.java'
text = read(path)
method = text.index('private void renderParticles(List<? extends IComponentParticleMorphRender>')
end = text.index('\n    }', method)
segment = text[method:end]
segment = segment.replace('BufferBuilder builder;', 'BufferBuilder builder = null;')
text = text[:method] + segment + text[end:]
write(path, text)

# Chroma sky/cloud targets in 1.21.1.
path = 'src/client/java/mchorse/blockbuster/mixin/client/WorldRendererMixin.java'
replace(path,
        'renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V',
        'renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V')
replace(path,
        'private void blockbuster$onRenderSky(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci)',
        'private void blockbuster$onRenderSky(Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci)')
replace(path,
        'renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V',
        'renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V')
replace(path,
        'private void blockbuster$onRenderClouds(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci)',
        'private void blockbuster$onRenderClouds(MatrixStack matrices, Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci)')

# Fixed-timestep mixin moved to the concrete Dynamic implementation.
path = 'src/client/java/mchorse/blockbuster/mixin/client/RenderTickCounterMixin.java'
replace(path, '@Mixin(RenderTickCounter.class)', '@Mixin(RenderTickCounter.Dynamic.class)')
replace(path, '    public float tickDelta;', '    private float tickDelta;')
replace(path, '    public float lastFrameDuration;', '    private float lastFrameDuration;')
replace(path,
        '@Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)',
        '@Inject(method = "beginRenderTick(JZ)I", at = @At("HEAD"), cancellable = true)')
replace(path,
        'private void blockbuster$onBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> info)',
        'private void blockbuster$onBeginRenderTick(long timeMillis, boolean tick, CallbackInfoReturnable<Integer> info)')

# HUD signatures now carry RenderTickCounter / focused state.
path = 'src/client/java/mchorse/blockbuster/mixin/client/ChatHudMixin.java'
replace(path,
        'render(Lnet/minecraft/client/gui/DrawContext;III)V',
        'render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V')
replace(path,
        'private void blockbuster$onRenderChat(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci)',
        'private void blockbuster$onRenderChat(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci)')

path = 'src/client/java/mchorse/blockbuster/mixin/client/InGameHudMixin.java'
add_import(path, 'import net.minecraft.client.render.RenderTickCounter;\n')
replace(path,
        'render(Lnet/minecraft/client/gui/DrawContext;F)V',
        'render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V')
replace(path,
        'private void blockbuster$onRenderHud(DrawContext context, float tickDelta, CallbackInfo ci)',
        'private void blockbuster$onRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci)')

# WorldRenderer's top-level render descriptor changed in 1.21.1.
new_render = 'render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V'
old_render = 'render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V'

path = 'src/client/java/mchorse/blockbuster/mixin/client/WorldRendererOutsidePlayerMixin.java'
replace(path, old_render, new_render)

path = 'src/client/java/mchorse/blockbuster/mixin/client/WorldRendererParticlesMixin.java'
add_import(path, 'import net.minecraft.client.render.RenderTickCounter;\n')
replace(path, old_render, new_render)
replace(path,
        'Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/Camera;F)V',
        'Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/Camera;F)V')
replace(path,
        'private void blockbuster$renderSnowstormParticles(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo ci)',
        'private void blockbuster$renderSnowstormParticles(RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci)')
needle = '        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();\n\n        MorphRenderContext.push(matrices, consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, tickDelta);'
replacement = '''        float tickDelta = tickCounter.getTickDelta(false);\n        MatrixStack matrices = new MatrixStack();\n        matrices.multiplyPositionMatrix(positionMatrix);\n        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();\n\n        MorphRenderContext.push(matrices, consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, tickDelta);'''
replace(path, needle, replacement)

print('Applied final compile fix and Minecraft 1.21.1 required mixin descriptors.')
