package mchorse.aperture.camera.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import mchorse.aperture.camera.FixtureRegistry;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.aperture.camera.fixtures.KeyframeFixture;
import mchorse.aperture.camera.fixtures.PathFixture;
import mchorse.aperture.camera.modifiers.RemapperModifier;
import mchorse.aperture.camera.values.ValueKeyframeChannel;
import mchorse.mclib.utils.keyframes.Keyframe;

/**
 * Fixture (de)serializer (P170).
 *
 * Port notes — the two <b>silent legacy on-load migrations</b> fire here:
 * a {@code "path"} fixture with {@code perPointDuration == true} converts
 * to a {@code KeyframeFixture} preserving per-point durations, and one with
 * primitive {@code useFactor == true} gets a {@code RemapperModifier}
 * prepended (keyframes on, channel copied from the path's speed channel,
 * {@code useSpeed} off). A re-save writes the migrated form — that is
 * 1.12.2 behavior. Constructor failures during {@code fromJSON} are
 * swallowed → null → skipped by callers (total reader). Byte format:
 * type byte + long duration + fixture bytes.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/json/FixtureSerializer.java
 */
public class FixtureSerializer
{
    /**
     * Write a camera fixture to byte buffer
     */
    public static void toBytes(AbstractFixture fixture, ByteBuf buffer)
    {
        byte type = FixtureRegistry.CLASS_TO_ID.get(fixture.getClass());

        buffer.writeByte(type);
        buffer.writeLong(fixture.getDuration());

        fixture.toBytes(buffer);
    }

    /**
     * Create an abstract camera fixture out of byte buffer
     */
    public static AbstractFixture fromBytes(ByteBuf buffer)
    {
        byte type = buffer.readByte();
        long duration = buffer.readLong();

        try
        {
            AbstractFixture fixture = FixtureRegistry.fromType(type, duration);

            fixture.fromBytes(buffer);

            return fixture;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Deserialize an abstract fixture from JsonElement.
     *
     * This method extracts type from the JSON map, and creates a fixture
     * from the type (which is mapped to a class). It also responsible for
     * setting the target for follow and look fixtures.
     */
    public static AbstractFixture fromJSON(JsonObject object)
    {
        AbstractFixture fixture = null;

        if (object.has("type"))
        {
            String type = object.get("type").getAsString();

            /* Special case for old per-point duration path */
            Class<? extends AbstractFixture> clazz = FixtureRegistry.NAME_TO_CLASS.get(type);

            if (clazz != null)
            {
                try
                {
                    fixture = clazz.getConstructor(long.class).newInstance(0);
                    fixture.fromJSON(object);
                }
                catch (Exception e)
                {}
            }

            /* Special case for per-point path */
            if (type.equals("path") && fixture != null)
            {
                if (object.has("perPointDuration") && object.get("perPointDuration").getAsBoolean())
                {
                    return convertToKeyframe((PathFixture) fixture, object.getAsJsonArray("points"));
                }
                else if (object.has("useFactor"))
                {
                    JsonElement useFactor = object.get("useFactor");

                    if (useFactor.isJsonPrimitive() && useFactor.getAsBoolean())
                    {
                        convertUseFactorToRemapper((PathFixture) fixture, object);
                    }
                }
            }
        }

        return fixture;
    }

    /**
     * Since I removed per-point duration from path fixture, I suppose I should
     * add a feature to not completely lose that data...
     */
    private static AbstractFixture convertToKeyframe(PathFixture fixture, JsonArray points)
    {
        if (points.size() != fixture.size())
        {
            return fixture;
        }

        KeyframeFixture keys = fixture.toKeyframe();
        int x = 0;

        for (int i = 0, c = fixture.size(); i < c; i++)
        {
            JsonObject point = points.get(i).getAsJsonObject();

            for (ValueKeyframeChannel channel : keys.channels)
            {
                Keyframe frame = channel.get().get(i);

                /* Total-reader deviation: legacy iterated all 8 channels and
                 * NPE'd on the always-empty "distance" channel (a bug from
                 * when the distance channel was added after this migration
                 * was written, which made perPointDuration profiles fail to
                 * load in late 1.8.x) — skip empty channels so the migration
                 * works as originally designed */
                if (frame != null)
                {
                    frame.tick = x;
                }
            }

            if (i == c - 1)
            {
                continue;
            }

            if (point.has("duration"))
            {
                x += point.get("duration").getAsInt();
            }
            else
            {
                x += 1;
            }
        }

        keys.setDuration(x);

        return keys;
    }

    /**
     * Convert use factor option to a remapper modifier
     */
    private static void convertUseFactorToRemapper(PathFixture fixture, JsonObject object)
    {
        RemapperModifier modifier = new RemapperModifier();

        modifier.keyframes.set(true);
        modifier.channel.copy(fixture.speed);

        fixture.useSpeed.set(false);
        fixture.modifiers.add(0, modifier);
    }

    /**
     * Serialize an abstract fixture into JsonElement.
     *
     * This method also responsible for giving the serialized abstract fixture
     * a type key (for later ability to deserialize exact type of the JSON
     * element) and target key for look and follow fixtures.
     */
    public static JsonObject toJSON(AbstractFixture fixtre)
    {
        JsonObject object = new JsonObject();

        object.addProperty("type", FixtureRegistry.NAME_TO_CLASS.inverse().get(fixtre.getClass()));
        fixtre.toJSON(object);

        return object;
    }
}
