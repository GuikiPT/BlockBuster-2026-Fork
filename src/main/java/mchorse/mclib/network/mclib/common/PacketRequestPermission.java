package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.permissions.PermissionCategory;
import mchorse.mclib.permissions.PermissionFactory;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Full port of McLib 2.4.3's PacketRequestPermission (roadmap P26).
 *
 * <p>Ported quirk (verbatim, do not "fix"): {@code getCallbackID()} uses
 * {@code Optional.of(...)} — unlike the {@code ofNullable} used elsewhere —
 * so it <b>NPEs when the callback id is unset (-1)</b>. In practice
 * {@code AbstractClientHandlerAnswer.requestServerAnswer} always sets the id
 * before send, so the NPE path never runs.</p>
 *
 * <p>{@code McLib.permissionFactory} is {@code PermissionFactory.INSTANCE}
 * until the McLib holder class lands (see P21 note there).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketRequestPermission.java</p>
 */
public class PacketRequestPermission implements IAnswerRequest<Boolean>
{
    private PermissionCategory request;
    private int callbackID = -1;

    public PacketRequestPermission()
    {}

    public PacketRequestPermission(int callbackID, PermissionCategory permission)
    {
        this.callbackID = callbackID;
        this.request = permission;
    }

    @Nullable
    public PermissionCategory getPermissionRequest()
    {
        return this.request;
    }

    @Override
    public void setCallbackID(int callbackID)
    {
        this.callbackID = callbackID;
    }

    @Override
    public Optional<Integer> getCallbackID()
    {
        return Optional.of(this.callbackID == -1 ? null : this.callbackID);
    }

    @Override
    public PacketBoolean getAnswer(Boolean value) throws NoSuchElementException
    {
        return new PacketBoolean(this.getCallbackID().get(), value);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.callbackID = buf.readInt();
        this.request = PermissionFactory.INSTANCE.getPermission(buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.callbackID);
        buf.writeInt(PermissionFactory.INSTANCE.getPermissionID(this.request));
    }
}
