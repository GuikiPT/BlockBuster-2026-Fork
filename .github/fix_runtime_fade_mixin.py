from pathlib import Path

path = Path("src/client/java/mchorse/metamorph/mixin/client/LivingEntityRendererFadeMixin.java")
text = path.read_text(encoding="utf-8")

old = '''    @ModifyArg(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
        ),
        index = 7
    )
    private float metamorph$fadeAlpha(float alpha)
    {
        return MorphRenderer.fadeAlphaOver(alpha);
    }
'''

new = '''    @ModifyArg(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
        ),
        index = 4
    )
    private int metamorph$fadeAlpha(int color)
    {
        int vanillaAlphaByte = color >>> 24 & 0xFF;
        float vanillaAlpha = vanillaAlphaByte / 255.0F;
        float fadedAlpha = MorphRenderer.fadeAlphaOver(vanillaAlpha);

        if (fadedAlpha == vanillaAlpha)
        {
            return color;
        }

        int fadedAlphaByte = Math.max(0, Math.min(255, Math.round(fadedAlpha * 255.0F)));

        return color & 0x00FFFFFF | fadedAlphaByte << 24;
    }
'''

if old not in text:
    raise SystemExit("Expected 1.20.4 fade injector was not found")

text = text.replace(old, new)
text = text.replace(
    "The 1.20.4 equivalent of that colour is the\n * four float arguments of {@code Model.render}, so the fade is applied by\n * rewriting the {@code alpha} one.",
    "The 1.21.1 equivalent is the packed ARGB colour passed to\n * {@code Model.render}, so the fade is applied by replacing only its alpha\n * byte while preserving the RGB channels."
)
text = text.replace(
    "and {@code EntityModel.render(MatrixStack, VertexConsumer, int, int, float,\n * float, float, float)} — one call site each inside",
    "and {@code Model.render(MatrixStack, VertexConsumer, int, int, int)} — one\n * call site each inside"
)

path.write_text(text, encoding="utf-8")
print("Updated LivingEntityRendererFadeMixin for Minecraft 1.21.1 packed model colors.")
