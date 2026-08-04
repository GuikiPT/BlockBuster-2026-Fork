from pathlib import Path
import re

ROOT = Path('.')
CLIENT = ROOT / 'src/client/java'


def add_import(text: str, statement: str) -> str:
    if statement in text:
        return text

    lines = text.splitlines(True)
    render_imports = [i for i, line in enumerate(lines) if line.startswith('import net.minecraft.client.render.')]
    imports = [i for i, line in enumerate(lines) if line.startswith('import ')]
    index = render_imports[-1] + 1 if render_imports else (imports[-1] + 1 if imports else 1)
    lines.insert(index, statement)

    return ''.join(lines)


for file in CLIENT.rglob('*.java'):
    text = file.read_text()
    original = text

    # Character filtering moved from SharedConstants to StringHelper.
    if 'SharedConstants.isValidChar' in text or 'SharedConstants.stripInvalidChars' in text:
        text = text.replace('SharedConstants.isValidChar', 'StringHelper.isValidChar')
        text = text.replace('SharedConstants.stripInvalidChars', 'StringHelper.stripInvalidChars')
        text = text.replace('import net.minecraft.SharedConstants;\n', 'import net.minecraft.util.StringHelper;\n')

        if 'import net.minecraft.util.StringHelper;' not in text:
            text = add_import(text, 'import net.minecraft.util.StringHelper;\n')

    # MinecraftClient exposes interpolation through RenderTickCounter in 1.21.
    text = text.replace('.getTickDelta()', '.getRenderTickCounter().getTickDelta(false)')

    # WorldRenderContext now exposes the RenderTickCounter rather than a raw float.
    text = text.replace('context.tickDelta()', 'context.tickCounter().getTickDelta(false)')

    # VertexConsumer.normal now accepts the MatrixStack entry directly.
    text = text.replace('.normal(entry.getNormalMatrix(),', '.normal(entry,')

    # VertexConsumer no longer has next(): completing the attribute chain emits the vertex.
    text = re.sub(r'(?m)^\s*\.next\(\);\s*$', '', text)
    text = re.sub(
        r'(?m)^(.*\.vertex\([^\n;]*?)\.next\(\);\s*$',
        lambda match: match.group(1) + ';',
        text,
    )
    text = text.replace('this.delegate.next();', '')

    # Tessellator owns begin() and BufferRenderer draws the BuiltBuffer in 1.21.
    mappings = []
    declaration = re.compile(
        r'(?m)^(\s*)BufferBuilder\s+(\w+)\s*=\s*(.+?)\.getBuffer\(\);\s*$'
    )

    def replace_declaration(match):
        indent, variable, source = match.groups()
        mappings.append((variable, source.strip()))
        return f'{indent}BufferBuilder {variable};'

    text = declaration.sub(replace_declaration, text)
    uses_buffer_renderer = False

    for variable, source in mappings:
        text, begin_count = re.subn(
            rf'(?m)^(\s*){re.escape(variable)}\.begin\(',
            rf'\1{variable} = {source}.begin(',
            text,
        )
        text, draw_count = re.subn(
            rf'{re.escape(source)}\.draw\(\);',
            f'BufferRenderer.drawWithGlobalProgram({variable}.end());',
            text,
        )
        uses_buffer_renderer |= draw_count > 0

    if uses_buffer_renderer:
        text = add_import(text, 'import net.minecraft.client.render.BufferRenderer;\n')

    if text != original:
        file.write_text(text)


# This debug recovery depended on Tessellator's old process-wide in-progress
# builder. 1.21 creates a new builder from begin(), so there is no global builder
# to inspect or clear after an unrelated renderer throws.
path = CLIENT / 'mchorse/metamorph/client/MorphRenderUtils.java'
text = path.read_text()
start = text.index('    public static void recoverSharedBuffer(AbstractMorph morph)')
end = text.index('\n    }', start) + len('\n    }')
text = text[:start] + '    public static void recoverSharedBuffer(AbstractMorph morph)\n    {}' + text[end:]
text = text.replace('import net.minecraft.client.render.BufferBuilder;\n', '')
path.write_text(text)


# The old defensive finally block used BufferBuilder.isBuilding()/clear(), both
# removed with the non-global 1.21 builder lifecycle.
path = CLIENT / 'mchorse/mclib/client/gui/framework/elements/GuiModelRenderer.java'
text = path.read_text()
text = re.sub(
    r'(?s)\n\s*/\* Never leave the process-wide tessellator buffer building.*?\n\s*if \(buffer\.isBuilding\(\)\)\n\s*\{\n\s*buffer\.clear\(\);\n\s*\}\n',
    '\n',
    text,
)
path.write_text(text)


# Morph particle components do not write to the billboard buffer; their legacy
# interface still carries the parameter, so null is the honest 1.21 replacement.
path = CLIENT / 'mchorse/blockbuster/client/particles/emitter/BedrockEmitter.java'
text = path.read_text().replace(
    '        BufferBuilder builder;\n\n        for (BedrockParticle particle : this.particles)\n',
    '        BufferBuilder builder = null;\n\n        for (BedrockParticle particle : this.particles)\n',
    1,
)
path.write_text(text)


# These helpers previously relied on Tessellator retaining the current builder.
# Pass the actual BufferBuilder to flush instead.
path = CLIENT / 'mchorse/mclib/client/gui/framework/elements/keyframes/GuiKeyframeElement.java'
text = path.read_text()
text = text.replace(
    '    protected static void flush()\n    {\n        Tessellator.getInstance().draw();\n    }',
    '    protected static void flush(BufferBuilder buffer)\n    {\n        BufferRenderer.drawWithGlobalProgram(buffer.end());\n    }',
)
text = add_import(text, 'import net.minecraft.client.render.BufferRenderer;\n')
path.write_text(text)

for relative in [
    'mchorse/mclib/client/gui/framework/elements/keyframes/GuiGraphView.java',
    'mchorse/mclib/client/gui/framework/elements/keyframes/GuiDopeSheet.java',
]:
    path = CLIENT / relative
    path.write_text(path.read_text().replace('flush();', 'flush(vb);'))

print('Applied Minecraft 1.21.1 bulk rendering-pipeline migration.')
