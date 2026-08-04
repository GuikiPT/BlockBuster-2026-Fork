package mchorse.mclib.client.gui.framework.elements.buttons;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiInventoryElement;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketDropItem;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiSlotElement} (roadmap P32).
 *
 * Parity note: the 1.12 armor-silhouette
 * item textures ({@code minecraft:textures/items/empty_armor_slot_*.png})
 * no longer exist as standalone files on 1.20.4, so byte-identical copies
 * are bundled under {@code assets/mclib/textures/gui/slots/} and the
 * constants point there.
 */
public class GuiSlotElement extends GuiClickElement<ItemStack>
{
    public static final ResourceLocation SHIELD = new ResourceLocation("mclib:textures/gui/slots/empty_armor_slot_shield.png");
    public static final ResourceLocation BOOTS = new ResourceLocation("mclib:textures/gui/slots/empty_armor_slot_boots.png");
    public static final ResourceLocation LEGGINGS = new ResourceLocation("mclib:textures/gui/slots/empty_armor_slot_leggings.png");
    public static final ResourceLocation CHESTPLATE = new ResourceLocation("mclib:textures/gui/slots/empty_armor_slot_chestplate.png");
    public static final ResourceLocation HELMET = new ResourceLocation("mclib:textures/gui/slots/empty_armor_slot_helmet.png");

    public GuiInventoryElement inventory;
    public final int slot;

    private ItemStack stack = ItemStack.EMPTY;

    public boolean drawDisabled = true;
    public int lastSlot;

    public GuiSlotElement(MinecraftClient mc, int slot, Consumer<ItemStack> callback)
    {
        super(mc, callback);

        this.slot = slot;
        this.inventory = new GuiInventoryElement(mc, this);

        this.flex().wh(24, 24);
    }

    public ItemStack getStack()
    {
        return this.stack;
    }

    public void setStack(ItemStack stack)
    {
        this.lastSlot = -1;
        this.stack = stack.copy();

        if (this.inventory.hasParent())
        {
            this.inventory.updateInventory();
        }
    }

    public void acceptStack(ItemStack stack, int slot)
    {
        this.lastSlot = slot;
        this.stack = stack.copy();

        if (this.callback != null)
        {
            this.callback.accept(stack);
        }
    }

    @Override
    public GuiContextMenu createContextMenu(GuiContext context)
    {
        if (this.contextMenu == null)
        {
            return this.createDefaultSlotContextMenu();
        }

        return super.createContextMenu(context);
    }

    public GuiSimpleContextMenu createDefaultSlotContextMenu()
    {
        GuiSimpleContextMenu menu = new GuiSimpleContextMenu(this.mc).action(Icons.COPY, IKey.lang("mclib.gui.item_slot.context.copy"), this::copyNBT);

        try
        {
            /* Total reader: invalid clipboard JSON/NBT falls through silently
             * (no paste entry). 1.20.4-format NBT; pre-flattening ids go
             * through the P71 id-translation shim later — unknown items
             * resolve to an empty stack, which also hides the entry. */
            ItemStack stack = ItemStack.fromNbt(StringNbtReader.parse(GuiUtils.getClipboardString()));

            if (!stack.isEmpty())
            {
                menu.action(Icons.PASTE, IKey.lang("mclib.gui.item_slot.context.paste"), () -> this.pasteItem(stack));
            }
        }
        catch (Exception e)
        {}

        return menu
            .action(Icons.DOWNLOAD, IKey.lang("mclib.gui.item_slot.context.drop"), this::dropItem)
            .action(Icons.CLOSE, IKey.lang("mclib.gui.item_slot.context.clear"), this::clearItem);
    }

    private void copyNBT()
    {
        if (!this.stack.isEmpty())
        {
            GuiUtils.setClipboardString(this.stack.writeNbt(new NbtCompound()).toString());
        }
    }

    private void pasteItem(ItemStack stack)
    {
        this.acceptStack(stack, -1);
    }

    private void dropItem()
    {
        if (!this.stack.isEmpty())
        {
            Dispatcher.sendToServer(new PacketDropItem(this.stack));
        }
    }

    private void clearItem()
    {
        this.acceptStack(ItemStack.EMPTY, -1);
    }

    @Override
    protected void click(int mouseButton)
    {
        this.inventory.removeFromParent();

        GuiContext context = GuiBase.getCurrent();

        this.inventory.flex().relative(context.screen.root).xy(0.5F, 0.5F).anchor(0.5F, 0.5F);
        this.inventory.resize();
        this.inventory.updateInventory();

        context.screen.root.add(this.inventory);
    }

    @Override
    protected ItemStack get()
    {
        return this.stack;
    }

    @Override
    protected void drawSkin(GuiContext context)
    {
        int border = this.inventory.hasParent() ? 0xff000000 + McLib.primaryColor.get() : 0xffffffff;

        if (McLib.enableBorders.get())
        {
            GuiDraw.drawRect(this.area.x + 1, this.area.y, this.area.ex() - 1, this.area.ey(), 0xff000000);
            GuiDraw.drawRect(this.area.x, this.area.y + 1, this.area.ex(), this.area.ey() - 1, 0xff000000);
            GuiDraw.drawRect(this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, border);
            GuiDraw.drawRect(this.area.x + 2, this.area.y + 2, this.area.ex() - 2, this.area.ey() - 2, 0xffc6c6c6);
        }
        else
        {
            GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
            GuiDraw.drawRect(this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, 0xffc6c6c6);
        }

        int x = this.area.mx() - 8;
        int y = this.area.my() - 8;

        if (this.stack.isEmpty() && this.slot != 0)
        {
            if (GuiDraw.getDrawContext() != null)
            {
                ResourceLocation silhouette = null;

                if (this.slot == 1)
                {
                    silhouette = SHIELD;
                }
                else if (this.slot == 2)
                {
                    silhouette = BOOTS;
                }
                else if (this.slot == 3)
                {
                    silhouette = LEGGINGS;
                }
                else if (this.slot == 4)
                {
                    silhouette = CHESTPLATE;
                }
                else if (this.slot == 5)
                {
                    silhouette = HELMET;
                }

                if (silhouette != null)
                {
                    RenderSystem.enableBlend();
                    RenderSystem.setShaderTexture(0, silhouette.toIdentifier());
                    GuiDraw.drawBillboard(x, y, 0, 0, 16, 16, 16, 16);
                    RenderSystem.disableBlend();
                }
            }
        }
        else
        {
            /* Legacy set up GUI item lighting + depth around the item quad;
             * DrawContext.drawItem owns that state on 1.20.4 */
            GuiInventoryElement.drawItemStack(this.stack, x, y, null);

            if (this.area.isInside(context))
            {
                context.tooltip.set(context, this);
            }
        }

        if (this.drawDisabled)
        {
            GuiDraw.drawLockedArea(this, McLib.enableBorders.get() ? 1 : 0);
        }
    }

    @Override
    public void drawTooltip(GuiContext context, Area area)
    {
        super.drawTooltip(context, area);

        GuiInventoryElement.drawItemTooltip(this.stack, this.mc == null ? null : this.mc.player, this.font, context.mouseX, context.mouseY);
    }
}
