package mchorse.mclib.utils.files.entries;

import mchorse.mclib.utils.resources.ResourceLocation;

import java.io.File;
import java.util.Objects;

/**
 * Port note: legacy imported {@code net.minecraft.util.ResourceLocation};
 * the port uses the bundled {@code mchorse.mclib.utils.resources
 * .ResourceLocation} (P15 decision).
 */
public class FileEntry extends AbstractEntry
{
    public ResourceLocation resource;

    public FileEntry(String title, File file, ResourceLocation resource)
    {
        super(title, file);

        this.resource = resource;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof FileEntry)
        {
            result = result && Objects.equals(this.resource, ((FileEntry) obj).resource);
        }

        return result;
    }
}
