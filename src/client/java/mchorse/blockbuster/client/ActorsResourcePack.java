package mchorse.blockbuster.client;

import java.io.InputStream;
import java.util.Set;
import mchorse.chameleon.mclib.ChameleonTree;

import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;

/**
 * Synthetic {@link ResourcePack} used only as the owning-pack handle for the
 * {@link net.minecraft.resource.Resource} objects the actor-pack mixin hands
 * back (roadmap P88). 1.12.2's {@code ActorsPack} <i>was</i> the resource pack;
 * 1.20.4 has no equivalent pluggable seam, so the real resolution lives in
 * {@link ActorsPack} and this class only satisfies {@code Resource}'s
 * {@code getPack()}/{@code getResourcePackName()} accessors.
 *
 * <p>It is never registered in the pack list, so its enumeration methods are
 * intentionally inert (legacy {@code getPackMetadata} returned {@code null} to
 * hide the pack from the resource-pack menu — same spirit).</p>
 */
public class ActorsResourcePack implements ResourcePack
{
    public static final ActorsResourcePack INSTANCE = new ActorsResourcePack();

    /**
     * Also covers the bundled Chameleon mod's {@code c.s} skin namespace: the
     * resource-manager mixin serves that through the same seam, so it hands back
     * this same synthetic owning pack.
     */
    private static final Set<String> NAMESPACES = Set.of(ActorsPack.DOMAIN, ActorsPack.HTTP, ActorsPack.HTTPS,
        ChameleonTree.DOMAIN);

    @Override
    public InputSupplier<InputStream> openRoot(String... segments)
    {
        return null;
    }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id)
    {
        return null;
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer)
    {}

    @Override
    public Set<String> getNamespaces(ResourceType type)
    {
        return NAMESPACES;
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader)
    {
        return null;
    }

    @Override
    public String getName()
    {
        return "Blockbuster's Actor Pack";
    }

    @Override
    public void close()
    {}
}
