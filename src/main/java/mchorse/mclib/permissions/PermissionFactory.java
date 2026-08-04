package mchorse.mclib.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * Port of McLib 2.4.3's PermissionFactory (minimal P21 subset landed with
 * S2/P26). This class is needed for serialisation, for example, into bytes to
 * reduce the amount of data being sent — permission ids on the wire are the
 * {@code toString()} hashcodes, exactly like legacy.
 *
 * <p>Legacy held the singleton on {@code McLib.permissionFactory}, which the
 * ported holder keeps as an alias of {@link #INSTANCE} — call sites keep
 * one-token diff-ability either way. Guava's {@code BiMap} is
 * replaced with two plain maps (guava isn't a guaranteed runtime dep of the
 * Fabric port; behavior identical for this usage).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/permissions/PermissionFactory.java</p>
 */
public class PermissionFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /** Legacy {@code McLib.permissionFactory} — that holder field aliases this instance. */
    public static final PermissionFactory INSTANCE = new PermissionFactory();

    /**
     * Map the permission string hashcode to the permission
     */
    private final Map<Integer, PermissionCategory> permissions = new HashMap<>();
    private final Map<PermissionCategory, Integer> ids = new HashMap<>();

    public void registerPermission(PermissionCategory permission)
    {
        String name = permission.toString();
        int hash = name.hashCode();

        if (this.permissions.containsKey(hash))
        {
            LOGGER.warn("The hash of the permission " + name + " is equal to the already registered permission " + this.permissions.get(hash));

            return;
        }

        if (!permission.hasChildren() && !this.ids.containsKey(permission))
        {
            this.permissions.put(hash, permission);
            this.ids.put(permission, hash);
        }
    }

    public boolean isRegistered(PermissionCategory permission)
    {
        return this.ids.containsKey(permission);
    }

    /**
     * @return the id of the permission or -1 if the provided permission was not registered
     */
    public int getPermissionID(PermissionCategory permission)
    {
        return Objects.requireNonNullElse(this.ids.get(permission), -1);
    }

    @Nullable
    public PermissionCategory getPermission(int id)
    {
        return this.permissions.get(id);
    }
}
