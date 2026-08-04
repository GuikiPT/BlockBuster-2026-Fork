package mchorse.aperture.camera;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.mclib.utils.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Fixture registry (P171).
 *
 * Port notes: byte IDs derive purely from registration order in
 * {@code CommonProxy.preLoad} (idle=0, dolly=1, circular=2, path=3,
 * keyframe=4, null=5, manual=6) — the ByteBuf sync format contract.
 * The legacy {@code @SideOnly(CLIENT)} {@code CLIENT} map is initialized
 * inline (harmless data; populated from the client entrypoint).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/FixtureRegistry.java
 */
public class FixtureRegistry
{
    /**
     * Bi-directional map between class and byte ID
     */
    public static final BiMap<Class<? extends AbstractFixture>, Byte> CLASS_TO_ID = HashBiMap.create();

    /**
     * Bi-directional map  map of fixtures types mapped to corresponding
     * class
     */
    public static final BiMap<String, Class<? extends AbstractFixture>> NAME_TO_CLASS = HashBiMap.create();

    /**
     * A mapping between string named to byte type of the fixture
     */
    public static final Map<String, Byte> NAME_TO_ID = new HashMap<String, Byte>();

    /**
     * Client information about camera fixtures, such as title, color, etc.
     */
    public static Map<Class<? extends AbstractFixture>, FixtureInfo> CLIENT = new HashMap<Class<? extends AbstractFixture>, FixtureInfo>();

    /**
     * Next available id
     */
    private static byte NEXT_ID = 0;

    public static byte getNextId()
    {
        return NEXT_ID;
    }

    public static FixtureInfo getInfo(byte type)
    {
        return FixtureRegistry.CLIENT.get(FixtureRegistry.CLASS_TO_ID.inverse().get(type));
    }

    /**
     * Create camera from type
     */
    public static AbstractFixture fromType(byte type, long duration) throws Exception
    {
        Class<? extends AbstractFixture> clazz = CLASS_TO_ID.inverse().get(type);

        if (clazz == null)
        {
            throw new Exception("Camera fixture by type '" + type + "' wasn't found!");
        }

        return clazz.getConstructor(long.class).newInstance(duration);
    }

    /**
     * Register given camera fixture
     */
    public static void register(String name, Class<? extends AbstractFixture> clazz)
    {
        if (CLASS_TO_ID.containsKey(clazz))
        {
            return;
        }

        CLASS_TO_ID.put(clazz, NEXT_ID);
        NAME_TO_ID.put(name, NEXT_ID);
        NAME_TO_CLASS.put(name, clazz);

        NEXT_ID++;
    }

    /**
     * Register client fixture information
     */
    public static void registerClient(Class<? extends AbstractFixture> clazz, String title, Color color)
    {
        Byte type = CLASS_TO_ID.get(clazz);

        if (type == null)
        {
            return;
        }

        CLIENT.put(clazz, new FixtureInfo(type.byteValue(), title, color));
    }

    /**
     * Fixture information
     */
    public static class FixtureInfo
    {
        public byte type;
        public String title;
        public Color color;

        public FixtureInfo(byte type, String title, Color color)
        {
            this.type = type;
            this.title = title;
            this.color = color;
        }
    }
}
