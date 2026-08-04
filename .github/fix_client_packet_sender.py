from pathlib import Path

path = Path("src/client/java/mchorse/mclib/network/ClientDispatcherHooks.java")
text = path.read_text(encoding="utf-8")

old = '''    public static void install()
    {
        mchorse.mclib.utils.RegistryUtils.setLookupSupplier(() -> net.minecraft.client.MinecraftClient.getInstance().world == null ? null : net.minecraft.client.MinecraftClient.getInstance().world.getRegistryManager());
    }
'''

new = '''    public static void install()
    {
        mchorse.mclib.utils.RegistryUtils.setLookupSupplier(() -> net.minecraft.client.MinecraftClient.getInstance().world == null ? null : net.minecraft.client.MinecraftClient.getInstance().world.getRegistryManager());

        /* Minecraft 1.21 uses typed CustomPayload packets. The common
         * dispatcher deliberately avoids classloading ClientPlayNetworking,
         * so install its client->server wire implementation here. Without
         * this seam every GUI/action packet (including metamorph:select_morph)
         * is encoded correctly but dropped before reaching the connection. */
        AbstractDispatcher.setClientSender((channel, buf) ->
            ClientPlayNetworking.send(RawPayload.of(channel, buf)));
    }
'''

if old not in text:
    raise SystemExit("Expected ClientDispatcherHooks.install implementation was not found")

path.write_text(text.replace(old, new), encoding="utf-8")
print("Installed the Fabric 1.21 client-to-server dispatcher sender.")
