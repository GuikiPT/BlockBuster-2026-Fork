package mchorse.mclib.events;

import mchorse.mclib.McLib;
import mchorse.mclib.permissions.DefaultPermissionLevel;
import mchorse.mclib.permissions.PermissionCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of McLib 2.4.3's {@code RegisterPermissionsEvent} (roadmap P21/P22).
 * Same registration surface; {@code extends Event} dropped (fired through
 * {@link McLibEvents#REGISTER_PERMISSIONS}). {@code loadPermissions} no
 * longer calls Forge's {@code PermissionAPI.registerNode} — vanilla op-level
 * checks live in {@link PermissionCategory#playerHasPermission}; the hook
 * point for an external permissions mod is {@link McLib#permissionFactory}.
 */
public class RegisterPermissionsEvent
{
    /**
     * List of the mods registered
     */
    private final List<PermissionCategory> mods = new ArrayList<>();
    /**
     * List of all permissions registered for fast access.
     */
    private final List<PermissionCategory> permissions = new ArrayList<>();

    private PermissionCategory currentMod;
    private PermissionCategory currentCategory;

    /**
     * Register a mod that holds permissions. This is necessary to register permissions or sub categories.
     * @param modid
     * @param level the default permission level.
     *              See {@link PermissionCategory#getDefaultPermission()} for implementation details how a level is inherited.
     */
    public void registerMod(String modid, DefaultPermissionLevel level)
    {
        this.currentMod = new PermissionCategory(modid, level);
        this.currentCategory = this.currentMod;

        this.mods.add(this.currentMod);
    }

    /**
     * register the provided permission category to the current category.
     * @param category
     * @throws UnsupportedOperationException if there is no current category
     */
    public void registerCategory(PermissionCategory category) throws UnsupportedOperationException
    {
        if (this.currentCategory == null) throw new UnsupportedOperationException("No current category to add this category to!");

        this.currentCategory.addChild(category);
        this.currentCategory = category;
    }

    /**
     * Register the permission at the last registered category
     * @param permission
     * @throws UnsupportedOperationException if no category is present to add the permission to
     */
    public void registerPermission(PermissionCategory permission) throws UnsupportedOperationException
    {
        if (this.currentCategory == null) throw new UnsupportedOperationException("No current category present to add the permission to!");

        this.currentCategory.addChild(permission);
        this.permissions.add(permission);
    }

    /**
     * Register the leaf permissions into the permission factory
     * {@link mchorse.mclib.McLib#permissionFactory}.
     */
    public void loadPermissions()
    {
        for (PermissionCategory permission : this.permissions)
        {
            if (permission.hasChildren()) continue;

            McLib.permissionFactory.registerPermission(permission);
        }
    }

    /**
     * End the current registered category and return to the parent category, so new permissions can be registered.
     * If the current category is the mod category, then the mod category will be ended,
     * so the registering process can start over with a new mod.
     */
    public void endCategory()
    {
        if (this.currentCategory == this.currentMod)
        {
            this.currentMod = null;
            this.currentCategory = null;
        }
        else
        {
            this.currentCategory = this.currentCategory.getParent();
        }
    }

    public void endMod()
    {
        this.currentMod = null;
        this.currentCategory = null;
    }
}
