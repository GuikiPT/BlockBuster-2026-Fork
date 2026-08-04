package mchorse.blockbuster.client.gui.utils;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.formats.obj.ShapeKey;
import mchorse.blockbuster_pack.client.gui.GuiShapeKeyListElement;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shape-keys list editor (port of Blockbuster 2.7.2, roadmap P144).
 *
 * <p>Shared between the model editor's poses tab (P137) and CustomMorph's pose
 * panel (S14). It exposes a sorting, name-rendered list of the current pose's
 * {@link ShapeKey}s plus a factor trackpad and a relative toggle that write
 * straight through to the selected key (no dirty tracking here — the owning
 * panel owns dirty state). The add context menu is nested: the top-level "add"
 * action replaces itself with a menu listing every key in {@code model.shapes}.
 * Factor and relative widgets are disabled (not hidden) when no key is
 * selected.</p>
 */
public class GuiShapeKeysEditor extends GuiElement
{
    public GuiListElement<ShapeKey> shapes;
    public GuiTrackpadElement factor;
    public GuiToggleElement relative;

    private Supplier<Model> supplier;

    public GuiShapeKeysEditor(MinecraftClient mc, Supplier<Model> supplier)
    {
        super(mc);

        this.supplier = supplier;

        this.shapes = new GuiShapeKeyListElement(mc, (str) -> this.setFactor(str.get(0)));
        this.shapes.sorting().background();
        this.shapes.context(() ->
        {
            GuiSimpleContextMenu menu = new GuiSimpleContextMenu(mc);

            menu.action(Icons.ADD, IKey.lang("blockbuster.gui.builder.context.add"), () ->
            {
                Model model = this.supplier == null ? null : this.supplier.get();

                if (model == null)
                {
                    return;
                }

                GuiSimpleContextMenu nested = new GuiSimpleContextMenu(mc);

                for (String key : model.shapes)
                {
                    nested.action(Icons.ADD, IKey.format("blockbuster.gui.builder.context.add_to", key), () -> this.addShapeKey(key));
                }

                GuiBase.getCurrent().replaceContextMenu(nested);
            });

            if (this.shapes.getIndex() != -1)
            {
                menu.action(Icons.REMOVE, IKey.lang("blockbuster.gui.builder.context.remove"), this::removeSelected);
            }

            return menu;
        });
        this.factor = new GuiTrackpadElement(mc, (value) -> this.setFactor(value.floatValue()));
        this.factor.tooltip(IKey.lang("blockbuster.gui.builder.shape_keys_factor_tooltip"), Direction.TOP);

        this.relative = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.builder.relative"), (b) -> this.shapes.getCurrentFirst().relative = b.isToggled());
        this.relative.tooltip(IKey.lang("blockbuster.gui.builder.relative_tooltip"), Direction.TOP);

        this.shapes.flex().relative(this).y(12).w(1F).hTo(this.factor.flex(), -17);
        this.factor.flex().relative(this.relative.flex()).y(-25).w(1F).h(20);
        this.relative.flex().relative(this).y(1F).w(1F).anchorY(1F);

        this.add(this.relative, this.factor, this.shapes);
    }

    /**
     * Insert a new shape key by name (the nested "add" context action).
     * Extracted from the legacy inline lambda unchanged so the CRUD is
     * headlessly testable; behavior is identical to 1.12.2.
     */
    public void addShapeKey(String key)
    {
        ShapeKey shapeKey = new ShapeKey(key, 0);

        this.shapes.getList().add(shapeKey);
        this.shapes.update();
        this.shapes.setCurrent(shapeKey);
        this.setFactor(shapeKey);
    }

    /**
     * Remove the selected shape key and clamp the selection index (the
     * "remove" context action). Extracted from the legacy inline lambda
     * unchanged.
     */
    public void removeSelected()
    {
        int index = this.shapes.getIndex();

        this.shapes.getList().remove(index);
        index = MathUtils.clamp(index, 0, this.shapes.getList().size() - 1);

        this.shapes.setIndex(index);
        this.setFactor(this.shapes.getCurrentFirst());
    }

    private void setFactor(ShapeKey key)
    {
        this.factor.setEnabled(key != null);
        this.relative.setEnabled(key != null);

        if (key != null)
        {
            this.factor.setValue(key.value);
            this.relative.toggled(key.relative);
        }
    }

    private void setFactor(float value)
    {
        this.shapes.getCurrentFirst().value = value;
    }

    public void fillData(List<ShapeKey> shapeKeys)
    {
        this.shapes.setList(shapeKeys);

        if (!shapeKeys.isEmpty())
        {
            this.shapes.setIndex(0);
            this.setFactor(this.shapes.getCurrentFirst());
        }
        else
        {
            this.setFactor(null);
        }
    }

    @Override
    public void draw(GuiContext context)
    {
        super.draw(context);

        if (this.shapes.isVisible())
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.builder.shape_keys"), this.shapes.area.x, this.shapes.area.y - 12, 0xffffff);
        }

        if (this.factor.isVisible())
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.builder.shape_keys_factor"), this.factor.area.x, this.factor.area.y - 12, 0xffffff);
        }
    }
}
