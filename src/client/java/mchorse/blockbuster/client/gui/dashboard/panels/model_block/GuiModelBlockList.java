package mchorse.blockbuster.client.gui.dashboard.panels.model_block;

import java.util.List;
import java.util.function.Consumer;

import mchorse.blockbuster.client.gui.dashboard.panels.GuiBlockList;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Model block list (roadmap P136).
 *
 * <p>Verbatim port of 1.12.2
 * {@code client/gui/dashboard/panels/model_block/GuiModelBlockList.java}.
 * {@link #addBlock(BlockPos)} accepts only {@link TileEntityModel}s (silently
 * returns {@code false} otherwise) and re-clamps the scroll on add;
 * {@link #drawElementPart} renders the tile's morph on-screen inside a scissor
 * window, draws the {@code "(x, y, z)"} label and a 1-px dark separator.</p>
 *
 * <p>Load-bearing quirk (recorded for P146): the hover text colour is the raw
 * decimal literal {@code 16777120} — <b>not</b> the usual {@code 0xffff80}
 * spelling — and is preserved verbatim.</p>
 */
public class GuiModelBlockList extends GuiBlockList<TileEntityModel>
{
    public GuiModelBlockList(MinecraftClient mc, IKey title, Consumer<List<TileEntityModel>> callback)
    {
        super(mc, title, callback);
    }

    @Override
    public boolean addBlock(BlockPos pos)
    {
        if (this.mc == null || this.mc.world == null)
        {
            /* Headless (unit test) context — same guard shape as GuiBase */
            return false;
        }

        BlockEntity tile = this.mc.world.getBlockEntity(pos);

        if (tile instanceof TileEntityModel)
        {
            this.list.add((TileEntityModel) tile);

            this.scroll.setSize(this.list.size());
            this.scroll.clamp();

            return true;
        }

        return false;
    }

    @Override
    protected void drawElementPart(TileEntityModel element, int i, int x, int y, boolean hover, boolean selected)
    {
        GuiContext context = GuiBase.getCurrent();
        int h = this.scroll.scrollItemSize;

        if (!element.morph.isEmpty())
        {
            int mny = MathHelper.clamp(y, this.scroll.y, this.scroll.ey());
            int mxy = MathHelper.clamp(y + 20, this.scroll.y, this.scroll.ey());

            if (mxy - mny > 0)
            {
                GuiDraw.scissor(x, mny, this.scroll.w, mxy - mny, context);
                element.morph.get().renderOnScreen(this.mc.player, x + this.scroll.w - 16, y + 30, 20, 1);
                GuiDraw.unscissor(context);
            }
        }

        BlockPos pos = element.getPos();
        String label = String.format("(%s, %s, %s)", pos.getX(), pos.getY(), pos.getZ());

        GuiDraw.drawStringWithShadow(this.font, label, x + 10, y + 6, hover ? 16777120 : 0xffffff);
        GuiDraw.drawRect(x, y + h - 1, x + this.area.w, y + h, 0x88181818);
    }
}
