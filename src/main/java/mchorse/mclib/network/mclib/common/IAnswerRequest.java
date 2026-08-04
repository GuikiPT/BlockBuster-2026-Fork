package mchorse.mclib.network.mclib.common;

import mchorse.mclib.network.IMessage;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Full port of McLib 2.4.3's IAnswerRequest (roadmap P26).
 *
 * <p>Callback-ID sentinel convention (defined here, consumed by
 * P115/P116 packets): the id field defaults to {@code -1} = "unset";
 * {@code Consumers.register} ids start at 0, so {@code -1} never collides.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/IAnswerRequest.java</p>
 */
public interface IAnswerRequest<T extends Serializable> extends IMessage
{
    void setCallbackID(int callbackID);

    Optional<Integer> getCallbackID();

    /**
     * Get an answer packet with the provided values and callbackID.
     * The generic type of the PacketAnswer needs to equal the type of the provided value.
     * @param value
     * @return a PacketAnswer containing the value of the type of this request.
     * @throws NoSuchElementException if {@link #getCallbackID()} value is not present.
     */
    PacketAnswer<T> getAnswer(T value) throws NoSuchElementException;
}
