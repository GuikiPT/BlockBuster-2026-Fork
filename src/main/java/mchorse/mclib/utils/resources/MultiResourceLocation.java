package mchorse.mclib.utils.resources;

import com.google.common.base.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Multiple resource location class
 *
 * This bad boy allows constructing a single texture out of several
 * {@link ResourceLocation}s. It doesn't really make sense for other
 * types of resources beside pictures.
 */
public class MultiResourceLocation extends ResourceLocation implements IWritableLocation<MultiResourceLocation>
{
    public List<FilteredResourceLocation> children = new ArrayList<FilteredResourceLocation>();

    private int id = -1;

    public MultiResourceLocation(String resourceName)
    {
        this();
        this.children.add(new FilteredResourceLocation(RLUtils.create(resourceName)));
    }

    public MultiResourceLocation(String resourceDomainIn, String resourcePathIn)
    {
        this();
        this.children.add(new FilteredResourceLocation(RLUtils.create(resourceDomainIn, resourcePathIn)));
    }

    public static MultiResourceLocation from(NbtElement nbt)
    {
        NbtList list = nbt instanceof NbtList ? (NbtList) nbt : null;

        if (list == null || list.isEmpty())
        {
            return null;
        }

        MultiResourceLocation multi = new MultiResourceLocation();

        try
        {
            multi.fromNbt(nbt);

            return multi;
        }
        catch (Exception e)
        {}

        return null;
    }

    public static MultiResourceLocation from(JsonElement element)
    {
        JsonArray list = element.isJsonArray() ? (JsonArray) element : null;

        if (list == null || list.size() == 0)
        {
            return null;
        }

        MultiResourceLocation multi = new MultiResourceLocation();

        try
        {
            multi.fromJson(element);

            return multi;
        }
        catch (Exception e)
        {}

        return null;
    }

    /** Namespace the composite is bound under in 1.20.4 (roadmap P91/P249). */
    public static final String NAMESPACE = "mclib";

    /** Path prefix the composite is bound under (see {@link #toIdentifier()}). */
    public static final String PREFIX = "multiskin/";

    public MultiResourceLocation()
    {
        /* This needed so there would less chances to match with an
         * actual ResourceLocation */
        super("it_would_be_very_ironic", "if_this_would_match_with_regular_rls");
    }

    public void recalculateId()
    {
        this.id = MultiResourceLocationManager.getId(this);
    }

    /** The registration id, computed on first use. {@code -1} when empty. */
    public int getId()
    {
        if (this.id < 0)
        {
            this.recalculateId();
        }

        return this.id;
    }

    /**
     * P249 (defect F) — a multiskin's own {@link net.minecraft.util.Identifier}.
     *
     * <p>Without this override the base implementation read the {@code domain}
     * and {@code path} <b>fields</b>, which are the sentinel
     * {@code it_would_be_very_ironic:if_this_would_match_with_regular_rls} the
     * no-arg constructor sets (the overridden {@code getResourceDomain()} /
     * {@code getResourcePath()} accessors are not what the base method uses). So
     * <i>every multiskin in existence</i> collapsed onto one identifier that no
     * resolver served: the composite never rendered, every multiskin aliased
     * every other one in the texture map, {@code RLUtils.getStreamForMultiskin}
     * had zero callers, and {@code Textures.MULTISKIN_KEYS} was never populated
     * so F3+T eviction was a no-op.</p>
     *
     * <p>Legacy needed no equivalent: {@code MultiResourceLocation} extended the
     * <i>vanilla</i> {@code ResourceLocation} with value-equality over
     * {@code children}, so it could be its own map key, and mclib's ASM coremod
     * branched on {@code instanceof MultiResourceLocation} inside
     * {@code SimpleReloadableResourceManager.getResource}. 1.20.4's
     * {@code Identifier} is final, so the port carries the identity in the path
     * instead: the {@link MultiResourceLocationManager} id — which is already
     * value-derived and deduplicated over {@code children} — is the composite's
     * name, and {@code MultiskinResources} is the branch.</p>
     */
    @Override
    public Identifier toIdentifier()
    {
        return new Identifier(NAMESPACE, PREFIX + this.getId());
    }

    @Override
    public String getResourceDomain()
    {
        return this.children.isEmpty() ? "" : this.children.get(0).path.getResourceDomain();
    }

    @Override
    public String getResourcePath()
    {
        return this.children.isEmpty() ? "" : this.children.get(0).path.getResourcePath();
    }

    /**
     * This is mostly for looks, but it doesn't really makes sense by
     * itself
     */
    @Override
    public String toString()
    {
        return this.getResourceDomain() + ":" + this.getResourcePath();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof MultiResourceLocation)
        {
            MultiResourceLocation multi = (MultiResourceLocation) obj;

            return Objects.equal(this.children, multi.children);
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode()
    {
        if (this.id < 0)
        {
            this.recalculateId();
        }

        return this.id;
    }

    @Override
    public void fromNbt(NbtElement nbt) throws Exception
    {
        NbtList list = (NbtList) nbt;

        for (int i = 0; i < list.size(); i++)
        {
            FilteredResourceLocation location = FilteredResourceLocation.from(list.get(i));

            if (location != null)
            {
                this.children.add(location);
            }
        }
    }

    @Override
    public void fromJson(JsonElement element) throws Exception
    {
        JsonArray array = (JsonArray) element;

        for (int i = 0; i < array.size(); i++)
        {
            FilteredResourceLocation location = FilteredResourceLocation.from(array.get(i));

            if (location != null)
            {
                this.children.add(location);
            }
        }
    }

    @Override
    public NbtElement writeNbt()
    {
        NbtList list = new NbtList();

        for (FilteredResourceLocation child : this.children)
        {
            /* Always a compound, exactly like legacy — which is also why the
             * list is homogeneous for free (1.12's appendTag silently dropped
             * mismatched tags; 1.20.4's NbtList.add throws). The earlier
             * "allDefault ? string : compound" dance existed only to work
             * around a short-form the legacy writer never emitted. S22 P287 */
            NbtElement tag = child.writeNbtCompound();

            if (tag != null)
            {
                list.add(tag);
            }
        }

        return list;
    }

    @Override
    public JsonElement writeJson()
    {
        JsonArray array = new JsonArray();

        for (FilteredResourceLocation child : this.children)
        {
            JsonElement element = child.writeJson();

            if (element != null)
            {
                array.add(element);
            }
        }

        return array;
    }

    @Override
    public MultiResourceLocation copy()
    {
        MultiResourceLocation newMulti = new MultiResourceLocation();

        for (FilteredResourceLocation child : this.children)
        {
            newMulti.children.add(child.copy());
        }

        return newMulti;
    }
}
