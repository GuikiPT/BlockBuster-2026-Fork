package mchorse.mclib.client.gui.framework.elements.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.ScrollArea;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.search.SearchManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Full port of McLib 2.4.3's {@code GuiInventoryElement} (roadmap P45; the
 * P32 agent shipped only the constructor/statics surface) — the floating
 * 200×140 item picker behind {@link GuiSlotElement}: player main inventory
 * (3 rows) + darker hotbar row, toggling to a creative-style item search.
 *
 * <p>1.20.4 mappings:</p>
 * <ul>
 * <li>legacy {@code NonNullList<ItemStack>} → yarn
 * {@code DefaultedList<ItemStack>}; the session-shared static
 * {@link #container} stays intentionally global (cleared on world-leave by
 * P44's KeyboardHandler).</li>
 * <li>legacy {@code mc.getSearchTree(SearchTreeManager.ITEMS)} →
 * {@code MinecraftClient.getSearchProvider(SearchManager.ITEM_TOOLTIP)}.
 * The {@link #searchProvider} seam replaces it headless/in tests (search
 * trees only exist after a resource reload).</li>
 * <li>legacy {@code mc.player.inventory.mainInventory} →
 * {@code mc.player.getInventory().main} (36 slots, hotbar = 0–8).</li>
 * <li>item quads through {@code DrawContext.drawItem}/{@code drawItemInSlot}
 * — DrawContext's z conventions (item 150, overlay 200, tooltip 400) map the
 * legacy z-200 contract "above panel, below tooltips"; legacy
 * {@code RenderHelper}/lightmap fiddling is handled by DrawContext.</li>
 * </ul>
 */
public class GuiInventoryElement extends GuiElement
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /** Session-shared search results (legacy static, intentionally global) */
    public static DefaultedList<ItemStack> container;

    /**
     * Test/headless seam: when non-null, {@link #searchItems} consults this
     * instead of the client search manager.
     *
     * <p><b>Deliberately unassigned in production</b> (S22/P242 audit): unlike
     * the port's other seams this one is an <i>override</i>, not a hole — the
     * real path below it is live ({@code MinecraftClient.getSearchProvider(
     * SearchManager.ITEM_TOOLTIP).findAll(query)}, the 1.20.4 spelling of
     * legacy's {@code mc.getSearchTree(SearchTreeManager.ITEMS).search(query)}).
     * Installing anything here would <i>replace</i> working item search, not
     * enable it. Leave null.</p>
     *
     * <p>Correction to the original P242 note: the client does <b>not</b> fill
     * that key on resource reload — see {@link #primeItemSearch}, which is what
     * actually makes the live path answer.</p>
     */
    public static Function<String, List<ItemStack>> searchProvider;

    public GuiTrackpadElement count;
    public GuiIconElement toggle;
    public GuiTextElement search;

    public GuiSlotElement slot;
    protected ScrollArea inventory = new ScrollArea(20);
    protected Area hotbar = new Area();

    private ItemStack active = ItemStack.EMPTY;
    private boolean searching;

    public static void drawItemStack(ItemStack stack, int x, int y, String altText)
    {
        drawItemStack(stack, x, y, 200, altText);
    }

    /**
     * Draws an ItemStack.
     *
     * Legacy rendered at {@code zLevel = z} (200 by default). DrawContext
     * already renders items at z 150 (overlay text at 200); non-default z
     * values shift relative to that default.
     */
    public static void drawItemStack(ItemStack stack, int x, int y, int z, String altText)
    {
        DrawContext context = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (context == null || mc == null || stack == null || stack.isEmpty())
        {
            return;
        }

        boolean offset = z != 200;

        if (offset)
        {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, z - 200);
        }

        context.drawItem(stack, x, y);
        context.drawItemInSlot(mc.textRenderer, stack, x, y, altText);

        if (offset)
        {
            context.getMatrices().pop();
        }
    }

    /**
     * Legacy {@code drawItemTooltip} — vanilla item tooltip at the given
     * mouse position. No-op headless.
     */
    public static void drawItemTooltip(ItemStack stack, PlayerEntity player, TextRenderer font, int x, int y)
    {
        DrawContext context = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (context == null || mc == null || font == null || stack == null || stack.isEmpty())
        {
            return;
        }

        TooltipContext flag = mc.options.advancedItemTooltips ? TooltipContext.ADVANCED : TooltipContext.BASIC;

        context.drawTooltip(font, stack.getTooltip(player, flag), Optional.empty(), x, y);
    }

    public GuiInventoryElement(MinecraftClient mc, GuiSlotElement slot)
    {
        super(mc);

        this.count = new GuiTrackpadElement(mc, (v) -> this.setCount(v.intValue()));
        this.count.limit(1).integer();
        this.toggle = new GuiIconElement(mc, Icons.SEARCH, this::toggleList);
        this.search = new GuiTextElement(mc, (t) -> this.updateList());
        this.search.setVisible(false);

        this.slot = slot;
        this.flex().wh(10 * 20, 7 * 20);

        this.count.flex().relative(this).x(10).y(10).w(1F, -40);
        this.search.flex().relative(this).x(10).y(10).w(1F, -40);
        this.toggle.flex().relative(this).x(1F, -30).y(10);

        this.add(this.count, this.toggle, this.search);

        this.inventory.scrollSpeed = 20;
    }

    private void setCount(int count)
    {
        ItemStack stack = this.slot.getStack().copy();

        stack.setCount(count);
        this.slot.acceptStack(stack, this.slot.lastSlot);
    }

    private void toggleList(GuiIconElement element)
    {
        this.searching = !this.searching;

        this.updateElements();
        this.updateList();
    }

    private void updateElements()
    {
        this.count.setVisible(!this.searching && !this.slot.getStack().isEmpty());
        this.search.setVisible(this.searching);
        this.inventory.h = this.searching ? 100 : 60;
    }

    private void updateList()
    {
        if (container == null)
        {
            container = DefaultedList.of();
        }

        container.clear();
        container.addAll(this.searchItems(this.search.field.getText().toLowerCase(Locale.ROOT)));

        this.inventory.scroll = 0;
        this.inventory.scrollSize = (int) (Math.ceil(container.size() / 9D) * this.inventory.scrollItemSize);
    }

    /**
     * Legacy {@code mc.getSearchTree(SearchTreeManager.ITEMS).search(...)};
     * total reader — no search manager (headless or pre-reload) yields an
     * empty result instead of crashing.
     */
    protected List<ItemStack> searchItems(String query)
    {
        if (searchProvider != null)
        {
            return searchProvider.apply(query);
        }

        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null)
            {
                primeItemSearch(mc);

                return mc.getSearchProvider(SearchManager.ITEM_TOOLTIP).findAll(query);
            }
        }
        catch (Exception e)
        {
            /* P285: this used to swallow silently, so a search manager that
             * failed on every query was indistinguishable from "no item matches
             * that" — the P280 shape. The empty list stays (legacy is a total
             * reader here), but the failure is now visible in the log. */
            LOGGER.warn("Item search for '{}' failed; the picker will show no results", query, e);
        }

        return Collections.emptyList();
    }

    /**
     * Build the creative item index {@link SearchManager#ITEM_TOOLTIP} reads,
     * when nothing has built it yet.
     *
     * <p>1.12.2 filled {@code SearchTreeManager.ITEMS} during the resource
     * reload, so {@code search("")} always answered with every creative
     * sub-item — which is why toggling this picker into search mode showed the
     * whole item list. 1.20.4 only <i>registers</i> the key
     * ({@code MinecraftClient.initializeSearchProviders}); the instance behind
     * it stays {@code ReloadableSearchProvider.empty()}, whose {@code findAll}
     * returns {@code List.of()} for <b>every</b> query and never throws. It is
     * replaced only when the creative search tab's stacks are collected
     * ({@code ItemGroup.updateEntries} → {@code reloadSearchProvider} →
     * {@code MinecraftClient.reloadSearchProvider}), and the sole vanilla
     * caller that collects them is {@code CreativeInventoryScreen}'s
     * constructor. So the picker's search grid was empty in any session where
     * the player had not opened the vanilla creative inventory at least once —
     * silently, because nothing failed and the P285 warning above never
     * fired.</p>
     *
     * <p>Primed with the same three arguments that constructor passes.
     * {@code updateDisplayContext} is idempotent — it compares the incoming
     * context against the stored one ({@code DisplayContext.doesNotMatch}) and
     * returns false without rebuilding — so the per-keystroke call costs an
     * equality check after the first, and the real creative screen is
     * unaffected (its {@code init} selects a tab unconditionally, and its
     * {@code updateDisplayParameters} fixup only has work to do when the
     * context actually changed).</p>
     */
    private static void primeItemSearch(MinecraftClient mc)
    {
        PlayerEntity player = mc.player;

        if (player == null || mc.options == null)
        {
            return;
        }

        ItemGroups.updateDisplayContext(
            player.getWorld().getEnabledFeatures(),
            player.isCreativeLevelTwoOp() && mc.options.getOperatorItemsTab().getValue(),
            player.getWorld().getRegistryManager());
    }

    private void setStack(ItemStack stack, int slot)
    {
        this.slot.acceptStack(stack, slot);

        this.updateElements();
        this.fillStack(this.slot.getStack());
    }

    public void updateInventory()
    {
        this.inventory.scroll = 0;

        this.searching = false;
        this.fillStack(this.slot.getStack());
        this.updateElements();
    }

    private void fillStack(ItemStack stack)
    {
        this.count.setVisible(!stack.isEmpty());
        this.count.limit(1, stack.getMaxCount());
        this.count.setValue(stack.getCount());
    }

    /**
     * Search-mode scrolling snaps to whole 9-item rows (legacy quirk) —
     * extracted from the legacy draw() body so it is headless-testable.
     */
    protected static int searchScrollOffset(int scroll, int scrollSize, int containerSize)
    {
        if (containerSize <= 45)
        {
            return 0;
        }

        int rows = (int) Math.ceil(containerSize / 9F);
        float factor = scroll / (float) scrollSize;
        int result = (int) (factor * rows);

        return result * 9;
    }

    /**
     * Legacy slot-index math from mouseClicked, extracted for headless tests:
     * returns the index into the backing list, or -1 when out of the grid.
     */
    protected static int gridIndex(Area area, int mouseX, int mouseY, boolean inventoryArea, boolean searching, int inventoryH)
    {
        int x = (mouseX - area.x - 2) / 20;
        int y = (mouseY - area.y - 2) / 20;

        if (inventoryArea && !searching)
        {
            y += 1;
        }

        if (x >= 9 || y >= (inventoryH / 20) + 1 || x < 0 || y < 0)
        {
            return -1;
        }

        return x + y * 9;
    }

    @Override
    public void resize()
    {
        super.resize();

        int tile = 20;
        int row = 9 * tile;
        int fourth = this.area.h / 4;

        this.inventory.set(this.area.mx(row), this.area.ey() - (fourth + tile) / 2 - tile * 4, row, (this.searching ? 5 : 3) * tile);
        this.hotbar.set(this.area.mx(row), this.area.ey() - (fourth + tile) / 2, row, tile);
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        if (super.mouseClicked(context))
        {
            return true;
        }

        if (!this.area.isInside(context))
        {
            this.removeFromParent();

            return false;
        }

        if (this.searching && this.inventory.mouseClicked(context))
        {
            return true;
        }

        boolean inventory = this.inventory.isInside(context);
        boolean hotbar = this.hotbar.isInside(context);

        if ((inventory || hotbar) && context.mouseButton == 0)
        {
            Area area = inventory ? this.inventory : this.hotbar;
            int index = gridIndex(area, context.mouseX, context.mouseY, inventory, this.searching, this.inventory.h);

            if (index == -1 || !this.isVisible())
            {
                return true;
            }

            if (this.slot != null)
            {
                List<ItemStack> items = this.searching ? container : this.playerInventory();

                if (this.searching)
                {
                    index += this.inventory.scroll / 20 * 9;
                }

                if (items != null && index < items.size())
                {
                    this.setStack(items.get(index), this.searching ? -1 : index);
                    this.removeFromParent();
                }

                return true;
            }
        }

        return false;
    }

    private DefaultedList<ItemStack> playerInventory()
    {
        return this.mc != null && this.mc.player != null ? this.mc.player.getInventory().main : null;
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        return super.mouseScrolled(context) || (this.searching && this.inventory.mouseScroll(context));
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        super.mouseReleased(context);

        this.inventory.mouseReleased(context);
    }

    @Override
    public void draw(GuiContext context)
    {
        this.active = null;

        /* Background rendering */
        if (GuiDraw.getDrawContext() != null)
        {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        }

        int border = 0xffffffff;
        int fourth = this.area.y(0.75F);

        if (McLib.enableBorders.get())
        {
            GuiDraw.drawRect(this.area.x + 1, this.area.y, this.area.ex() - 1, this.area.ey(), 0xff000000);
            GuiDraw.drawRect(this.area.x, this.area.y + 1, this.area.ex(), this.area.ey() - 1, 0xff000000);
            GuiDraw.drawRect(this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, border);
            GuiDraw.drawRect(this.area.x + 2, this.area.y + 2, this.area.ex() - 2, this.area.ey() - 2, 0xffc6c6c6);

            if (!this.searching)
            {
                GuiDraw.drawRect(this.area.x + 1, fourth, this.area.ex() - 1, this.area.ey() - 1, 0xff222222);
            }
        }
        else
        {
            GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
            GuiDraw.drawRect(this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, 0xffc6c6c6);

            if (!this.searching)
            {
                GuiDraw.drawRect(this.area.x, fourth, this.area.ex(), this.area.ey(), 0xff222222);
            }
        }

        GuiDraw.drawDropCircleShadow(this.toggle.area.mx(), this.toggle.area.my(), 10, 4, 8, 0x18000000, 0);

        if (this.searching)
        {
            if (container != null)
            {
                int scroll = searchScrollOffset(this.inventory.scroll, this.inventory.scrollSize, container.size());
                int index = this.drawGrid(context, this.inventory, container, -1, scroll, scroll + this.inventory.h / 20 * 9);

                if (index != -1)
                {
                    this.active = container.get(index);
                }
            }
        }
        else
        {
            DefaultedList<ItemStack> inventory = this.playerInventory();

            if (inventory != null)
            {
                int index = this.drawGrid(context, this.inventory, inventory, -1, 9, inventory.size());
                index = this.drawGrid(context, this.hotbar, inventory, index, 0, 9);

                if (index != -1)
                {
                    this.active = inventory.get(index);
                }
            }
        }

        if (this.active != null)
        {
            context.tooltip.set(context, this);
        }

        GuiDraw.drawLockedArea(this, McLib.enableBorders.get() ? 1 : 0);

        if (this.searching)
        {
            this.inventory.drag(context);

            GuiDraw.scissor(this.inventory.x, this.inventory.y, this.inventory.w, this.inventory.h, context);
            this.inventory.drawScrollbar();
            GuiDraw.unscissor(context);
        }

        super.draw(context);
    }

    private int drawGrid(GuiContext context, Area area, List<ItemStack> inventory, int index, int i, int c)
    {
        for (int j = 0; j < c - i; j++)
        {
            int k = i + j;

            if (k >= inventory.size())
            {
                return index;
            }

            ItemStack stack = inventory.get(k);

            int x = j % 9;
            int y = j / 9;

            x = area.x + 2 + 20 * x;
            y = area.y + 2 + 20 * y;

            int diffX = context.mouseX - x;
            int diffY = context.mouseY - y;

            boolean hover = diffX >= 0 && diffX < 18 && diffY >= 0 && diffY < 18;

            GuiDraw.drawRect(x - 1, y - 1, x + 17, y + 17, area == this.hotbar ? 0xaa000000 : 0x44000000);

            drawItemStack(stack, x, y, null);

            if (hover)
            {
                GuiDraw.drawRect(x - 2, y - 2, x + 18, y + 18, 0xcc000000 + McLib.primaryColor.get());
                index = k;
            }
        }

        return index;
    }

    @Override
    public void drawTooltip(GuiContext context, Area area)
    {
        super.drawTooltip(context, area);

        GuiInventoryElement.drawItemTooltip(this.active, this.mc != null ? this.mc.player : null, this.font, context.mouseX, context.mouseY);
    }
}
