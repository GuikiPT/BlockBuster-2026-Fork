package mchorse.aperture.camera;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import mchorse.aperture.camera.modifiers.AbstractModifier;
import mchorse.mclib.client.gui.utils.keys.LangKey;
import mchorse.mclib.utils.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Modifier registry (P174).
 *
 * Port notes: byte IDs = registration order in {@code CommonProxy.preLoad}
 * (angle=0, translate=1, shake=2, drag=3, look=4, follow=5, orbit=6,
 * math=7, remapper=8, dolly_zoom=9; Blockbuster's "tracker"=10 is
 * S14-adjacent and deferred with TrackerModifier). {@code getType} returns
 * −1 for unregistered (serialized as dropped, never a crash);
 * {@code fromType} throws (caught upstream → modifier dropped).
 * {@code ModifierInfo.getTitle()} uses the port's {@code LangKey}
 * translator seam instead of client-only {@code I18n} (echoes the key
 * headlessly).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/ModifierRegistry.java
 */
public class ModifierRegistry
{
    /**
     * Bi-directional map between class and byte ID
     */
    public static final BiMap<Class<? extends AbstractModifier>, Byte> CLASS_TO_ID = HashBiMap.create();

    /**
     * Registered modifier name mapped to a class.
     */
    public static final BiMap<String, Class<? extends AbstractModifier>> NAME_TO_CLASS = HashBiMap.create();

    /**
     * Client information about camera modifier
     */
    public static Map<Class<? extends AbstractModifier>, ModifierInfo> CLIENT = new HashMap<Class<? extends AbstractModifier>, ModifierInfo>();

    /**
     * Next available id
     */
    private static byte NEXT_ID = 0;

    public static byte getNextId()
    {
        return NEXT_ID;
    }

    /**
     * Get type from abstract modifier
     */
    public static byte getType(AbstractModifier modifier)
    {
        Byte type = CLASS_TO_ID.get(modifier.getClass());

        return type == null ? -1 : type.byteValue();
    }

    /**
     * Create an abstract modifier from given byte
     */
    public static AbstractModifier fromType(byte type) throws Exception
    {
        Class<? extends AbstractModifier> clazz = CLASS_TO_ID.inverse().get(type);

        if (clazz != null)
        {
            return clazz.getConstructor().newInstance();
        }

        throw new Exception("Modifier with type '" + type + "' not exists!");
    }

    /**
     * Register given modifier
     */
    public static void register(String name, Class<? extends AbstractModifier> clazz)
    {
        if (CLASS_TO_ID.containsKey(clazz))
        {
            return;
        }

        CLASS_TO_ID.put(clazz, NEXT_ID);
        NAME_TO_CLASS.put(name, clazz);

        NEXT_ID++;
    }

    /**
     * Register client information
     */
    public static void registerClient(Class<? extends AbstractModifier> clazz, String title, Color color)
    {
        Byte type = CLASS_TO_ID.get(clazz);

        if (type == null)
        {
            return;
        }

        CLIENT.put(clazz, new ModifierInfo(type.byteValue(), title, color));
    }

    /**
     * Modifier information
     */
    public static class ModifierInfo
    {
        public byte type;
        public String title;
        public Color color;

        public ModifierInfo(byte type, String title, Color color)
        {
            this.type = type;
            this.title = title;
            this.color = color;
        }

        public String getTitle()
        {
            return LangKey.translator.apply(this.title, new Object[0]);
        }
    }
}
