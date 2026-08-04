package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormAppearanceSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormCollisionAppearanceSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormCollisionLightingSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormCollisionSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormExpirationSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormGeneralSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormInitializationSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormLifetimeSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormLightingSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormMotionSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormParticleMorphSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormRateSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormShapeSection;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections.GuiSnowstormSpaceSection;
import mchorse.blockbuster.client.particles.BedrockLibrary;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringSearchListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiConfirmModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDrawable;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Port of Blockbuster 2.7.2's {@code GuiSnowstorm} (roadmap P141) — the
 * particle-editor dashboard panel shell.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/GuiSnowstorm.java</p>
 *
 * <p><b>Shell phase (P141):</b> the scheme-list modal (add / dupe / remove /
 * folder), the save + dirty-tracking flow, the {@link GuiSnowstormRenderer}
 * viewport, and the {@link #editor} scroll column are all wired here. The 13
 * concrete section editors are S13/P155 work; the shell freezes their
 * registration contract via {@link #SECTION_ORDER} and the public
 * {@link #addSection(GuiSnowstormSection)} seam, so S13 slots them in without
 * touching the shell.</p>
 *
 * <p><b>Parity quirks preserved:</b></p>
 * <ul>
 *   <li>{@link #DEFAULT_PARTICLE} ({@code "default_snow"}) is the initial
 *   selection and the template every "add" clones.</li>
 *   <li>Switching schemes <b>discards</b> unsaved edits <b>silently</b> — legacy
 *   {@link #setScheme(String, BedrockScheme)} unconditionally clears
 *   {@code dirty} with no confirm prompt (verified against legacy source).</li>
 *   <li>The particle-morph section registration is commented out in legacy
 *   (constructor line 124) and stays out here — see {@link #SECTION_ORDER}.</li>
 *   <li>Panel state (current scheme + dirty flag) survives dashboard rebuilds:
 *   the panels holder deliberately keeps this instance on teardown (P135).</li>
 * </ul>
 */
public class GuiSnowstorm extends GuiBlockbusterPanel
{
    public static final String DEFAULT_PARTICLE = "default_snow";

    /**
     * The frozen section-registration order (roadmap P141 verification: legacy
     * constructor lines 123–136). S13/P155 must register its concrete
     * {@link GuiSnowstormSection}s in exactly this order via
     * {@link #addSection(GuiSnowstormSection)}. The particle-morph section is
     * intentionally absent (legacy line 124 commented out).
     */
    public static final List<String> SECTION_ORDER = Collections.unmodifiableList(Arrays.asList(
        "GuiSnowstormGeneralSection",
        /* //this.addSection(this.particleMorphSection); — legacy line 124 (kept commented) */
        "GuiSnowstormSpaceSection",
        "GuiSnowstormInitializationSection",
        "GuiSnowstormRateSection",
        "GuiSnowstormLifetimeSection",
        "GuiSnowstormShapeSection",
        "GuiSnowstormMotionSection",
        "GuiSnowstormExpirationSection",
        "GuiSnowstormAppearanceSection",
        "GuiSnowstormLightingSection",
        "GuiSnowstormCollisionSection",
        "GuiSnowstormCollisionAppearanceSection",
        "GuiSnowstormCollisionLightingSection"
    ));

    public GuiSnowstormRenderer renderer;
    public GuiScrollElement editor;

    public GuiIconElement open;
    public GuiIconElement save;

    public GuiElement modal;
    public GuiIconElement add;
    public GuiIconElement dupe;
    public GuiIconElement remove;
    public GuiIconElement folder;
    public GuiStringSearchListElement particles;

    public List<GuiSnowstormSection> sections = new ArrayList<GuiSnowstormSection>();

    /**
     * The dormant particle-morph section (legacy constructor line 113). It is
     * constructed and its morph picker is (re)bound in {@link #appear()}, but it
     * is <b>never</b> added to the {@link #editor} column — legacy line 124 is
     * commented out. Preserved verbatim for parity.
     */
    public GuiSnowstormParticleMorphSection particleMorphSection;

    private BedrockLibrary library;

    private String filename;
    private BedrockScheme scheme;
    private boolean dirty;

    public GuiSnowstorm(MinecraftClient mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        /* TODO: Add link to snowstorm web editor */
        this.library = library();

        this.renderer = new GuiSnowstormRenderer(mc);
        this.renderer.flex().relative(this).wh(1F, 1F);

        this.editor = new GuiScrollElement(mc);
        this.editor.flex().relative(this).x(1F).w(200).h(1F).anchorX(1F).column(20).vertical().stretch().scroll().padding(10);

        this.open = new GuiIconElement(mc, Icons.MORE, (b) -> this.modal.toggleVisible());
        this.open.tooltip(IKey.lang("blockbuster.gui.snowstorm.open_tooltip"));
        this.open.flex().relative(this);
        this.save = new GuiIconElement(mc, Icons.SAVE, (b) -> this.save());
        this.save.tooltip(IKey.lang("blockbuster.gui.snowstorm.save_tooltip"));
        this.save.flex().relative(this.open).x(20);

        /* Modal */
        this.modal = new GuiElement(mc);
        this.modal.flex().relative(this).y(20).w(160).hTo(this.area, 1F, -16);

        GuiLabel label = Elements.label(IKey.lang("blockbuster.gui.snowstorm.title"), 20)
            .anchor(0, 0.5F);
        label.flex().relative(this.modal).xy(10, 10).w(1F, -20);

        this.add = new GuiIconElement(mc, Icons.ADD, (b) -> this.addEffect());
        this.add.tooltip(IKey.lang("blockbuster.gui.snowstorm.add_tooltip"));
        this.dupe = new GuiIconElement(mc, Icons.DUPE, (b) -> this.dupeEffect());
        this.dupe.tooltip(IKey.lang("blockbuster.gui.snowstorm.dupe_tooltip"));
        this.remove = new GuiIconElement(mc, Icons.REMOVE, (b) -> this.removeEffect());
        this.folder = new GuiIconElement(mc, Icons.FOLDER, (b) -> GuiUtils.openFolder(this.library.folder.getAbsolutePath()));
        this.folder.tooltip(IKey.lang("blockbuster.gui.snowstorm.folder_tooltip"));

        this.particles = new GuiStringSearchListElement(mc, (list) -> this.setScheme(list.get(0)));
        this.particles.flex().relative(this.modal).xy(10, 35).w(1F, -20).h(1F, -45);

        /* S13/P155: the particle-morph section is constructed but never added
         * in legacy (constructor line 113 + commented line 124). It is omitted
         * from the shell entirely — see SECTION_ORDER. */

        GuiElement icons = new GuiElement(mc);
        icons.flex().relative(this.modal).x(1F, -10).y(10).h(20).anchorX(1F).row(0).resize().width(20).height(20);
        icons.add(this.add, this.dupe, this.remove, this.folder);

        this.modal.add(label, icons, this.particles);
        this.modal.setVisible(false);
        this.add(this.renderer, new GuiDrawable(this::drawOverlay), this.editor, this.modal, this.open, this.save);

        /* S13/P155: register the concrete section editors in SECTION_ORDER
         * (legacy GuiSnowstorm constructor lines 123–136). The particle-morph
         * section (legacy line 124) stays commented out — it is dormant and
         * unreachable in 1.12.2. */
        this.particleMorphSection = new GuiSnowstormParticleMorphSection(mc, this);

        this.addSection(new GuiSnowstormGeneralSection(mc, this));
        //this.addSection(this.particleMorphSection); // legacy line 124 (kept commented)
        this.addSection(new GuiSnowstormSpaceSection(mc, this));
        this.addSection(new GuiSnowstormInitializationSection(mc, this));
        this.addSection(new GuiSnowstormRateSection(mc, this));
        this.addSection(new GuiSnowstormLifetimeSection(mc, this));
        this.addSection(new GuiSnowstormShapeSection(mc, this));
        this.addSection(new GuiSnowstormMotionSection(mc, this));
        this.addSection(new GuiSnowstormExpirationSection(mc, this));
        this.addSection(new GuiSnowstormAppearanceSection(mc, this));
        this.addSection(new GuiSnowstormLightingSection(mc, this));
        this.addSection(new GuiSnowstormCollisionSection(mc, this));
        this.addSection(new GuiSnowstormCollisionAppearanceSection(mc, this));
        this.addSection(new GuiSnowstormCollisionLightingSection(mc, this));

        this.keys()
            .register(IKey.lang("blockbuster.gui.snowstorm.keys.save"), LegacyKeyCodes.KEY_S, () -> this.save.clickItself(GuiBase.getCurrent()))
            .held(LegacyKeyCodes.KEY_LCONTROL).category(IKey.lang("blockbuster.gui.snowstorm.keys.category"));
    }

    /**
     * The client-side particle library — legacy {@code Blockbuster.proxy.particles},
     * read exactly as legacy read it.
     *
     * <p><b>Reader only, deliberately.</b> This method used to <i>build</i> the
     * library when the holder was null (and {@code reload()} it, which the legacy
     * editor never did). That made the panel the only writer of
     * {@code ClientProxy.particles} in the whole tree, so the library existed
     * only for a session in which the user happened to open this panel — the
     * S22 batch U-K defect. Now
     * {@link mchorse.blockbuster.client.particles.ParticleLibraryWiring#install()}
     * builds it at client init, and a second writer here would be strictly
     * harmful: it could replace the instance live emitters hold their
     * {@code BedrockScheme} identity against ({@code SnowstormClient.reloadIfStale}),
     * forcing every emitter to rebuild.</p>
     */
    private static BedrockLibrary library()
    {
        return ClientProxy.particles;
    }

    private void addEffect()
    {
        GuiModal.addFullModal(this.modal, () -> new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.snowstorm.add_modal"), (name) ->
        {
            if (this.library.hasEffect(name) || name.isEmpty())
            {
                return;
            }

            BedrockScheme scheme = this.library.load(DEFAULT_PARTICLE);

            scheme.identifier = name;
            this.setScheme(name, scheme);
            this.dirty();

            this.particles.list.setCurrent("");
        }));
    }

    private void dupeEffect()
    {
        GuiModal.addFullModal(this.modal, () -> new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.snowstorm.dupe_modal"), (name) ->
        {
            if (this.library.hasEffect(name) || name.isEmpty())
            {
                return;
            }

            if (!this.scheme.isFactory())
            {
                this.particles.list.setCurrent("");
            }

            BedrockScheme scheme = BedrockScheme.dupe(this.scheme);

            scheme.factory(this.library.factory.containsKey(name));
            scheme.identifier = name;
            this.setScheme(name, scheme);
            this.save();
        }).setValue(this.filename));
    }

    private void removeEffect()
    {
        if (this.scheme.isFactory())
        {
            return;
        }

        GuiModal.addFullModal(this.modal, () -> new GuiConfirmModal(this.mc, IKey.lang("blockbuster.gui.snowstorm.remove_modal"), (confirm) ->
        {
            if (!confirm || !this.library.hasEffect(this.filename))
            {
                return;
            }

            int index = this.particles.list.getIndex();

            if (this.library.file(this.filename).delete())
            {
                if (!this.library.factory.containsKey(this.filename))
                {
                    this.particles.list.remove(this.filename);
                }

                index = MathUtils.clamp(index, 0, this.particles.list.getList().size() - 1);

                this.particles.list.setIndex(index);
                this.setScheme(this.particles.list.getCurrentFirst());
            }
        }));
    }

    public void dirty()
    {
        this.dirty = true;
        this.updateSaveButton();

        /* Total: never crash if the emitter has no scheme yet (legacy always
         * had one set by this point). */
        if (this.renderer.emitter != null && this.renderer.emitter.scheme != null)
        {
            this.renderer.emitter.setupVariables();
        }
    }

    private void updateSaveButton()
    {
        this.save.both(this.dirty ? Icons.SAVE : Icons.SAVED);
    }

    private void updateRemoveButton()
    {
        this.remove.setEnabled(!this.scheme.isFactory());

        if (this.remove.isEnabled())
        {
            this.remove.tooltip(IKey.lang("blockbuster.gui.snowstorm.remove_tooltip"));
        }
        else
        {
            this.remove.tooltip(IKey.lang("blockbuster.gui.snowstorm.remove_factory_tooltip"));
        }
    }

    private void save()
    {
        for (GuiSnowstormSection section : this.sections)
        {
            section.beforeSave(this.scheme);
        }

        boolean saved = this.library.save(this.filename, this.scheme);

        if (!this.particles.list.getList().contains(this.filename))
        {
            this.particles.list.add(this.filename);
            this.particles.list.sort();
            this.particles.list.setCurrent(this.filename);
        }

        /* P284: only claim the preset is saved if it actually is. Clearing the
         * flag on a failed write let the next scheme switch discard the edits
         * silently. */
        this.dirty = !saved;
        this.scheme.factory(false);
        this.updateSaveButton();
        this.updateRemoveButton();
    }

    /**
     * Shell↔section registration seam (public for S13/P155). Registers a
     * section in {@link #sections} (in call order — S13 must call in
     * {@link #SECTION_ORDER}) and mounts it into the {@link #editor} column.
     */
    public void addSection(GuiSnowstormSection section)
    {
        this.sections.add(section);
        this.editor.add(section);
    }

    private void setScheme(String scheme)
    {
        this.setScheme(scheme, this.library.load(scheme));
    }

    private void setScheme(String name, BedrockScheme scheme)
    {
        if (scheme == null)
        {
            this.particles.list.remove(name);
            this.particles.list.setIndex(-1);

            return;
        }

        this.filename = name;
        this.scheme = scheme;
        this.renderer.setScheme(this.scheme);

        this.dirty = false;
        this.updateSaveButton();
        this.updateRemoveButton();

        for (GuiSnowstormSection section : this.sections)
        {
            section.setScheme(this.scheme);
        }

        this.editor.resize();
    }

    @Override
    public void appear()
    {
        super.appear();

        String current = this.particles.list.getCurrentFirst();

        this.particles.filter("", true);

        this.particles.list.clear();
        this.particles.list.add(this.library.presets.keySet());
        this.particles.list.sort();

        if (this.scheme == null)
        {
            this.setScheme(DEFAULT_PARTICLE);
            this.particles.list.setCurrent(DEFAULT_PARTICLE);
        }
        else
        {
            this.particles.list.setCurrent(current);
        }

        /* S13/P155: legacy also re-armed the morph picker for the (disabled)
         * particle-morph section here:
         *   ClientProxy.panels.picker(this.particleMorphSection::setMorph);
         * omitted with the section. */
    }

    @Override
    public void close()
    {
        if (this.renderer.emitter != null)
        {
            this.renderer.emitter.particles.clear();
        }
    }

    private void drawOverlay(GuiContext context)
    {
        /* Draw debug info */
        BedrockEmitter emitter = this.renderer.emitter;
        String label = emitter.particles.size() + "P - " + emitter.age + "A";

        GuiDraw.drawStringWithShadow(this.font, label, this.area.x + 4, this.area.ey() - 12, 0xffffff);

        if (this.modal.isVisible())
        {
            this.open.area.draw(ColorUtils.HALF_BLACK);
            this.modal.area.draw(ColorUtils.HALF_BLACK);
        }
    }
}
