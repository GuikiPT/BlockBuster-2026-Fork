package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Slime model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelSlime}. This shim <b>overrides
 * {@code render}, not {@code setRotationAngles}</b>: it pushes the matrix,
 * translates {@code (0, -1, 0)}, renders {@code head} (its {@code right_eye} /
 * {@code left_eye} / {@code mouth} children recurse), then renders the
 * translucent {@code outer} jelly shell with blend — and, when the model's name
 * is <b>not</b> {@code "Slime"}, forces the outer pass to white at alpha
 * {@code 0.8}, exactly as the 1.12.2 {@code GlStateManager.color(1,1,1,0.8)}
 * did.</p>
 *
 * <p>The eye/mouth fields are reflection-injected by P75 but never referenced
 * here — they render as children of {@code head}.</p>
 */
public class ModelSlime extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer head;
    public ModelCustomRenderer right_eye;
    public ModelCustomRenderer left_eye;
    public ModelCustomRenderer mouth;
    public ModelCustomRenderer outer;

    public ModelSlime(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {}

    @Override
    public void render(MatrixStack matrices, VertexConsumer consumer, float r, float g, float b, float a, int light, int overlay)
    {
        matrices.push();
        matrices.translate(0.0F, -1.0F, 0.0F);

        if (this.head != null)
        {
            this.head.render(matrices, consumer, 0.0625F, r, g, b, a, light, overlay);
        }

        if (this.outer != null)
        {
            /* Legacy: for non-"Slime" models GlStateManager.color(1,1,1,0.8F)
             * forced the outer shell to white at 0.8 alpha; the "Slime" morph
             * kept the ambient colour (passthrough here). The blend enable is a
             * GL render-state concern handled by the P80 render layer. */
            if (this.model.name.equals("Slime"))
            {
                this.outer.render(matrices, consumer, 0.0625F, r, g, b, a, light, overlay);
            }
            else
            {
                this.outer.render(matrices, consumer, 0.0625F, 1.0F, 1.0F, 1.0F, 0.8F, light, overlay);
            }
        }

        matrices.pop();

        this.current = null;
    }
}
