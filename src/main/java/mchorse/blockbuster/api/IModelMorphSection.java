package mchorse.blockbuster.api;

/**
 * Seam for the custom-morph factory section that mirrors the model registry
 * (roadmap P69 ↔ S4).
 *
 * <p>In 1.12.2, {@code ModelHandler} kept the model map and the
 * {@code blockbuster_pack} morph list in lock-step through
 * {@code Blockbuster.proxy.factory.section.add(key, model, client)} /
 * {@code section.remove(key)} — removing a model unregisters its morph so the
 * creative morph picker never shows ghosts.</p>
 *
 * <p><b>SEAM(S4):</b> the concrete {@code BlockbusterFactory} morph section is
 * ported in the morph stage (S4). Until it wires itself in through
 * {@link ModelHandler#morphSection}, model add/remove is a no-op on the morph
 * side. Headless tests inject a recording double here to assert the add/remove
 * call sequence.</p>
 */
public interface IModelMorphSection
{
    /**
     * @param client mirrors the legacy boolean: {@code false} from the
     *               server/common {@code ModelHandler}, {@code true} from the
     *               client {@code ModelClientHandler}.
     */
    void add(String key, Model model, boolean client);

    void remove(String key);
}
