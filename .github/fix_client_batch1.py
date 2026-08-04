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
            raise RuntimeError(f'Expected text not found in {path}: {old[:120]!r}')
        return
    write(path, text.replace(old, new))


# PlayerModelPart moved out of the client renderer package.
replace(
    'src/client/java/mchorse/vanilla_pack/morphs/PlayerMorphClientEntity.java',
    'import net.minecraft.client.render.entity.PlayerModelPart;\n',
    'import net.minecraft.entity.player.PlayerModelPart;\n',
)

# Iris 1.8.x retained uniformOrder but removed the old parallel "uniforms" list.
path = 'src/client/java/mchorse/blockbuster/mixin/client/iris/CustomUniformsAccessor.java'
text = read(path)
text, count = re.subn(
    r'\n\s*@Accessor\(value = "uniforms", remap = false\)\n\s*List blockbuster\$uniforms\(\);',
    '',
    text,
)
if count != 1:
    raise RuntimeError(f'Expected one obsolete Iris uniforms accessor, removed {count}')
write(path, text)

# Armor materials now provide one or more explicit texture layers; dye colour is
# stored in the DYED_COLOR component rather than implemented by DyeableItem.
path = 'src/client/java/mchorse/blockbuster_pack/client/render/layers/LayerActorArmor.java'
text = read(path)
text = text.replace('import net.minecraft.item.DyeableItem;\n', '')
if 'import net.minecraft.component.type.DyedColorComponent;\n' not in text:
    text = text.replace(
        'import net.minecraft.client.util.math.MatrixStack;\n',
        'import net.minecraft.client.util.math.MatrixStack;\nimport net.minecraft.component.type.DyedColorComponent;\n',
    )
if 'import net.minecraft.item.ArmorMaterial;\n' not in text:
    text = text.replace(
        'import net.minecraft.item.ArmorItem;\n',
        'import net.minecraft.item.ArmorItem;\nimport net.minecraft.item.ArmorMaterial;\n',
    )

start = text.index('    private void renderArmorSlot(')
end = text.index('\n    private BipedEntityModel<LivingEntity> getModelForSlot', start)
replacement = '''    private void renderArmorSlot(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ItemStack stack, ArmorItem item, ModelCustomRenderer limb, float scale)\n    {\n        BipedEntityModel<LivingEntity> model = this.getModelForSlot(item.getSlotType());\n\n        matrices.push();\n\n        limb.postRender(matrices, scale);\n\n        ModelPart part = this.applyArmorLimb(matrices, model, limb.limb, limb.limb.slot);\n\n        if (part != null)\n        {\n            boolean secondLayer = limb.limb.slot.slot == EquipmentSlot.LEGS;\n\n            for (ArmorMaterial.Layer layer : item.getMaterial().value().layers())\n            {\n                int color = layer.isDyeable()\n                    ? DyedColorComponent.getColor(stack, 0xA06540)\n                    : 0xFFFFFF;\n                float r = (color >> 16 & 255) / 255F;\n                float g = (color >> 8 & 255) / 255F;\n                float b = (color & 255) / 255F;\n                VertexConsumer consumer = vertexConsumers.getBuffer(\n                    RenderLayer.getArmorCutoutNoCull(layer.getTexture(secondLayer)));\n\n                part.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, r, g, b, 1F);\n            }\n\n            if (stack.hasGlint())\n            {\n                VertexConsumer glint = vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint());\n\n                part.render(matrices, glint, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);\n            }\n        }\n\n        matrices.pop();\n    }\n'''
text = text[:start] + replacement + text[end:]

old_texture = '''    public Identifier getArmorTexture(ArmorItem item, EquipmentSlot slot, String type)\n    {\n        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;\n        String material = item.getMaterial().getName();\n        String path = "textures/models/armor/" + material + "_layer_" + layer + (type == null ? "" : "_" + type) + ".png";\n\n        return Identifier.of(path);\n    }\n'''
new_texture = '''    public Identifier getArmorTexture(ArmorItem item, EquipmentSlot slot, String type)\n    {\n        java.util.List<ArmorMaterial.Layer> layers = item.getMaterial().value().layers();\n        int index = type == null ? 0 : Math.min(1, layers.size() - 1);\n\n        return layers.get(index).getTexture(slot == EquipmentSlot.LEGS);\n    }\n'''
if old_texture not in text:
    raise RuntimeError('Expected legacy getArmorTexture implementation')
text = text.replace(old_texture, new_texture)
write(path, text)

# In 1.21 eye height is part of EntityDimensions itself. Override the player's
# base dimensions and attach the morph eye height to the returned record.
path = 'src/main/java/mchorse/metamorph/mixin/PlayerEntityDimensionsMixin.java'
text = read(path)
text = text.replace('@Inject(method = "getDimensions",', '@Inject(method = "getBaseDimensions",')
old_body = '''        if (this.metamorph$active)\n        {\n            cir.setReturnValue(EntityDimensions.changing(this.metamorph$width, this.metamorph$height));\n        }\n    }\n\n    @Inject(method = "getActiveEyeHeight", at = @At("HEAD"), cancellable = true)\n    private void metamorph$onGetActiveEyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir)\n    {\n        if (this.metamorph$active && this.metamorph$applyEye)\n        {\n            cir.setReturnValue(this.metamorph$eye);\n        }\n    }\n'''
new_body = '''        if (this.metamorph$active)\n        {\n            EntityDimensions dimensions = EntityDimensions.changing(this.metamorph$width, this.metamorph$height);\n\n            if (this.metamorph$applyEye)\n            {\n                dimensions = dimensions.withEyeHeight(this.metamorph$eye);\n            }\n\n            cir.setReturnValue(dimensions);\n        }\n    }\n'''
if old_body not in text:
    raise RuntimeError('Expected legacy morph dimension/eye injections')
write(path, text.replace(old_body, new_body))

print('Applied Minecraft 1.21.1 client migration batch 1.')
