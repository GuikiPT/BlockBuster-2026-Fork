package mchorse.blockbuster.client.model.parsing;

/**
 * Extension hook for hardcoded/generated models (roadmap P75, consumed by P81).
 *
 * <p>Direct port of Blockbuster 2.7.2's
 * {@code client/model/parsing/IModelCustom}. {@link #onGenerated()} is invoked
 * by {@link ModelParser} once the limb graph has been built and same-named
 * limb renderers have been reflection-injected into the model's public fields.</p>
 */
public interface IModelCustom
{
    void onGenerated();
}
