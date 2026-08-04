package mchorse.mclib.permissions;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IByteBufSerializable;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of McLib 2.4.3's PermissionCategory (minimal P21 subset landed with
 * S2/P26 — {@code PacketRequestPermission} needs it). Wire layout identical
 * to legacy: UTF8 name + level ordinal + children (recursive). Note the
 * inherited legacy quirk: {@code toBytes} NPEs on a null {@code level} —
 * only fully-leveled categories were ever serialized in 1.12.2; kept as-is.
 *
 * <p>{@code playerHasPermission} replaces Forge's {@code PermissionAPI}
 * lookup with the default-handler semantics (ALL → true, OP → op check,
 * NONE → false) resolved through the parent chain, which is exactly what
 * Forge's {@code DefaultPermissionHandler} did with no permission mod
 * installed. TODO(P21): pluggable permission-provider seam.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/permissions/PermissionCategory.java</p>
 */
public class PermissionCategory implements IByteBufSerializable
{
    private String name;
    private final List<PermissionCategory> children = new ArrayList<>();
    private PermissionCategory parent;

    /**
     * If null, then this permission takes the level of the parent
     */
    private DefaultPermissionLevel level;

    public PermissionCategory(String name)
    {
        this(name, null);
    }

    public PermissionCategory(String name, DefaultPermissionLevel level)
    {
        this.name = name;
        this.level = level;
    }

    private PermissionCategory()
    {}

    public boolean playerHasPermission(PlayerEntity player)
    {
        switch (this.getDefaultPermission())
        {
            case ALL:
                return true;

            case OP:
                return player instanceof ServerPlayerEntity && OpHelper.isPlayerOp((ServerPlayerEntity) player);

            default:
                return false;
        }
    }

    public void addChild(PermissionCategory category)
    {
        this.children.add(category);
        category.parent = this;
    }

    public boolean hasChildren()
    {
        return !this.children.isEmpty();
    }

    public List<PermissionCategory> getChildren()
    {
        return new ArrayList<>(this.children);
    }

    /**
     * @return the parent or null if no parent is present.
     */
    public PermissionCategory getParent()
    {
        return this.parent;
    }

    /**
     * Gets the default permission based on this or the parent's default permission level.
     * @return the permission level of this permission or if null of the parent.
     *         If no parent above has a default permission level, this method will return {@link DefaultPermissionLevel#NONE}
     */
    public DefaultPermissionLevel getDefaultPermission()
    {
        if (this.level == null)
        {
            return (this.parent != null) ? this.parent.getDefaultPermission() : DefaultPermissionLevel.NONE;
        }

        return this.level;
    }

    @Override
    public String toString()
    {
        return (this.parent != null ? this.parent + "." : "") + this.name;
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        this.name = ForgeByteBufUtils.readUTF8String(buffer);
        this.level = DefaultPermissionLevel.values()[buffer.readInt()];

        int size = buffer.readInt();

        for (int i = 0; i < size; i++)
        {
            PermissionCategory child = new PermissionCategory();

            child.fromBytes(buffer);

            this.addChild(child);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        ForgeByteBufUtils.writeUTF8String(buffer, this.name);
        buffer.writeInt(this.level.ordinal());

        buffer.writeInt(this.children.size());

        for (PermissionCategory category : this.children)
        {
            category.toBytes(buffer);
        }
    }
}
