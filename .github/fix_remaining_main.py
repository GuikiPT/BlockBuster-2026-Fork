from pathlib import Path

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    file = ROOT / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(text)


def replace(path: str, old: str, new: str, required: bool = True, count: int = -1) -> None:
    file = ROOT / path
    text = file.read_text()
    if old not in text:
        if required:
            raise RuntimeError(f'Expected text not found in {path}: {old[:140]!r}')
        return
    file.write_text(text.replace(old, new, count))


def add_import(path: str, anchor: str, statement: str) -> None:
    text = read(path)
    if statement in text:
        return
    replace(path, anchor, anchor + statement)


# Remaining common-source signatures and registry types.
replace('src/main/java/mchorse/blockbuster/common/item/ItemGun.java',
        'public int getMaxUseTime(ItemStack stack)\n',
        'public int getMaxUseTime(ItemStack stack, LivingEntity user)\n', required=False)
replace('src/main/java/mchorse/blockbuster/common/item/ItemGun.java',
        'public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack)\n',
        'public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack)\n', required=False)

path = 'src/main/java/mchorse/blockbuster/common/block/BlockDirector.java'
replace(path,
        'public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)',
        'protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)', required=False)
text = read(path)
if text.count('Hand') == 1:
    write(path, text.replace('import net.minecraft.util.Hand;\n', ''))

replace('src/main/java/mchorse/blockbuster/recording/scene/fake/FakeClientConnection.java',
        'public void setPacketListener(PacketListener listener)\n',
        'public void setInitialPacketListener(PacketListener listener)\n', required=False)
replace('src/main/java/mchorse/metamorph/entity/EntityMorph.java',
        'public EntityDimensions getDimensions(EntityPose pose)\n',
        'public EntityDimensions getBaseDimensions(EntityPose pose)\n', required=False)
replace('src/main/java/mchorse/metamorph/MetamorphCommon.java',
        '.dimensions(EntityMorph.DEFAULT_SIZE.width,\n                    EntityMorph.DEFAULT_SIZE.height)',
        '.dimensions(EntityMorph.DEFAULT_SIZE.width(),\n                    EntityMorph.DEFAULT_SIZE.height())', required=False)

for path, old, new in [
    ('src/main/java/mchorse/vanilla_pack/actions/SmallFireball.java',
     'new SmallFireballEntity(world, target, d2, d3, d4)',
     'new SmallFireballEntity(world, target, new Vec3d(d2, d3, d4))'),
    ('src/main/java/mchorse/vanilla_pack/actions/Fireball.java',
     'new FireballEntity(world, target, d2, d3, d4, 1)',
     'new FireballEntity(world, target, new Vec3d(d2, d3, d4), 1)'),
    ('src/main/java/mchorse/vanilla_pack/actions/FireBreath.java',
     'new DragonFireballEntity(target.getWorld(), target, d2, d3, d4)',
     'new DragonFireballEntity(target.getWorld(), target, new Vec3d(d2, d3, d4))'),
]:
    replace(path, old, new, required=False)

path = 'src/main/java/mchorse/vanilla_pack/abilities/PotionAbility.java'
add_import(path, 'import net.minecraft.entity.effect.StatusEffect;\n', 'import net.minecraft.registry.entry.RegistryEntry;\n')
replace(path, 'protected StatusEffect potion;', 'protected RegistryEntry<StatusEffect> potion;', required=False)

path = 'src/main/java/mchorse/vanilla_pack/abilities/StepUp.java'
add_import(path, 'import net.minecraft.entity.LivingEntity;\n',
           'import net.minecraft.entity.attribute.EntityAttributeInstance;\nimport net.minecraft.entity.attribute.EntityAttributes;\n')
replace(path, 'target.setStepHeight(1.0f);', 'setStepHeight(target, 1.0D);', required=False)
replace(path, 'target.setStepHeight(0.6f);', 'setStepHeight(target, 0.6D);', required=False)
text = read(path)
if 'private static void setStepHeight' not in text:
    helper = '''\n    private static void setStepHeight(LivingEntity target, double height)\n    {\n        EntityAttributeInstance attribute = target.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);\n\n        if (attribute != null)\n        {\n            attribute.setBaseValue(height);\n        }\n    }\n'''
    write(path, text.rsplit('\n}', 1)[0] + helper + '}\n')
replace(path,
        '1.20.4 with a public {@link net.minecraft.entity.Entity#setStepHeight(float)}\n * setter (step height only becomes an attribute in 1.20.5 — not here).',
        '1.21.1 exposes step height through {@link EntityAttributes#GENERIC_STEP_HEIGHT}.', required=False)

replace('src/main/java/mchorse/vanilla_pack/abilities/SunAllergy.java',
        'target.sendEquipmentBreakStatus(EquipmentSlot.HEAD);',
        'target.sendEquipmentBreakStatus(itemstack.getItem(), EquipmentSlot.HEAD);', required=False)

# Modern GameProfile codec bridge.
write('src/main/java/mchorse/mclib/utils/GameProfileNbtUtils.java', r'''package mchorse.mclib.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.DataResult;
import mchorse.mclib.McLib;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Registry-independent GameProfile NBT bridge for Minecraft 1.21. */
public final class GameProfileNbtUtils
{
    private GameProfileNbtUtils()
    {}

    public static NbtCompound write(NbtCompound target, GameProfile profile)
    {
        if (profile == null)
        {
            return target;
        }

        DataResult<NbtElement> result = ProfileComponent.CODEC.encodeStart(NbtOps.INSTANCE, new ProfileComponent(profile));
        NbtElement encoded = result.resultOrPartial(message -> McLib.LOGGER.warn("Couldn't encode game profile: {}", message)).orElse(null);

        if (encoded instanceof NbtCompound compound)
        {
            target.copyFrom(compound);
        }

        return target;
    }

    public static GameProfile read(NbtCompound tag)
    {
        GameProfile decoded = ProfileComponent.CODEC.parse(NbtOps.INSTANCE, tag)
            .result()
            .map(ProfileComponent::gameProfile)
            .orElse(null);

        if (decoded != null)
        {
            return decoded;
        }

        String name = tag.contains("Name", NbtElement.STRING_TYPE) ? tag.getString("Name") : "";
        UUID id = null;

        try
        {
            if (tag.containsUuid("Id"))
            {
                id = tag.getUuid("Id");
            }
            else if (tag.contains("Id", NbtElement.STRING_TYPE))
            {
                id = UUID.fromString(tag.getString("Id"));
            }
        }
        catch (Exception ignored)
        {}

        if (id == null)
        {
            id = Uuids.getOfflinePlayerUuid(name);
        }

        GameProfile profile = new GameProfile(id, name);

        if (tag.contains("Properties", NbtElement.COMPOUND_TYPE))
        {
            NbtCompound properties = tag.getCompound("Properties");

            for (String key : properties.getKeys())
            {
                NbtList values = properties.getList(key, NbtElement.COMPOUND_TYPE);

                for (int i = 0; i < values.size(); i++)
                {
                    NbtCompound value = values.getCompound(i);
                    String propertyValue = value.getString("Value");
                    Property property = value.contains("Signature", NbtElement.STRING_TYPE)
                        ? new Property(key, propertyValue, value.getString("Signature"))
                        : new Property(key, propertyValue);

                    profile.getProperties().put(key, property);
                }
            }
        }

        return profile;
    }
}
''')

path = 'src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java'
add_import(path, 'import mchorse.mclib.utils.NBTUtils;\n', 'import mchorse.mclib.utils.GameProfileNbtUtils;\n')
replace(path, 'import net.minecraft.nbt.NbtHelper;\n', '', required=False)
replace(path, 'NbtHelper.writeGameProfile(profileTag, this.profile);',
        'GameProfileNbtUtils.write(profileTag, this.profile);', required=False)
replace(path, 'this.profile = NbtHelper.toGameProfile(tag.getCompound("PlayerProfile"));',
        'this.profile = GameProfileNbtUtils.read(tag.getCompound("PlayerProfile"));', required=False)
replace(path, '!Util.isBlank(this.profile.getName())',
        'this.profile.getName() != null && !this.profile.getName().isBlank()', required=False)

# Fabric 1.21 typed custom payload adapter.
write('src/main/java/mchorse/mclib/network/RawPayload.java', r'''package mchorse.mclib.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Typed 1.21 payload envelope preserving the port's existing channel and byte contracts. */
public record RawPayload(CustomPayload.Id<RawPayload> id, byte[] data) implements CustomPayload
{
    private static final Map<Identifier, CustomPayload.Id<RawPayload>> IDS = new HashMap<>();
    private static final Map<Identifier, PacketCodec<RegistryByteBuf, RawPayload>> CODECS = new HashMap<>();
    private static final Set<Identifier> C2S_TYPES = new HashSet<>();
    private static final Set<Identifier> S2C_TYPES = new HashSet<>();

    public static synchronized CustomPayload.Id<RawPayload> id(Identifier channel)
    {
        return IDS.computeIfAbsent(channel, CustomPayload.Id::new);
    }

    public static synchronized PacketCodec<RegistryByteBuf, RawPayload> codec(Identifier channel)
    {
        return CODECS.computeIfAbsent(channel, key -> PacketCodec.of(
            (payload, buf) -> buf.writeBytes(payload.data),
            buf ->
            {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new RawPayload(id(key), data);
            }));
    }

    public static synchronized void registerC2S(Identifier channel)
    {
        if (C2S_TYPES.add(channel))
        {
            PayloadTypeRegistry.playC2S().register(id(channel), codec(channel));
        }
    }

    public static synchronized void registerS2C(Identifier channel)
    {
        if (S2C_TYPES.add(channel))
        {
            PayloadTypeRegistry.playS2C().register(id(channel), codec(channel));
        }
    }

    public static RawPayload of(Identifier channel, PacketByteBuf buf)
    {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return new RawPayload(id(channel), data);
    }

    public PacketByteBuf toBuffer()
    {
        return new PacketByteBuf(Unpooled.wrappedBuffer(this.data));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId()
    {
        return this.id;
    }
}
''')

path = 'src/main/java/mchorse/mclib/network/AbstractDispatcher.java'
replace(path, 'ServerPlayNetworking.send(player, channel, buf);',
        'ServerPlayNetworking.send(player, RawPayload.of(channel, buf));', required=False)
replace(path,
        'ServerPlayNetworking.registerGlobalReceiver(channel, (mcServer, player, handler, buf, responseSender) -> this.receiveServer(channel, buf, player));',
        'RawPayload.registerC2S(channel);\n        ServerPlayNetworking.registerGlobalReceiver(RawPayload.id(channel),\n            (payload, context) -> this.receiveServer(channel, payload.toBuffer(), context.player()));', required=False)
text = read(path)
needle = '''        if (side == Side.SERVER)\n        {\n            registration.handler();\n            this.wireRegisterServerReceiver(channel);\n        }\n'''
if needle in text and 'RawPayload.registerS2C(channel);' not in text[text.index(needle):text.index(needle)+len(needle)+120]:
    write(path, text.replace(needle, needle + '''        else\n        {\n            RawPayload.registerS2C(channel);\n        }\n'''))
text = read(path)
needle = '''        if (!this.chunkWired)\n        {\n            this.chunkWired = true;\n            this.wireRegisterServerReceiver(this.chunkChannel);\n        }\n'''
if needle in text:
    write(path, text.replace(needle, '''        if (!this.chunkWired)\n        {\n            this.chunkWired = true;\n            RawPayload.registerS2C(this.chunkChannel);\n            this.wireRegisterServerReceiver(this.chunkChannel);\n        }\n'''))

path = 'src/client/java/mchorse/mclib/network/ClientDispatcherHooks.java'
replace(path, 'AbstractDispatcher.setClientSender(ClientPlayNetworking::send);',
        'AbstractDispatcher.setClientSender((channel, buf) -> ClientPlayNetworking.send(RawPayload.of(channel, buf)));', required=False)
replace(path,
        'ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, responseSender) -> dispatcher.receiveClient(channel, buf));',
        'RawPayload.registerS2C(channel);\n            ClientPlayNetworking.registerGlobalReceiver(RawPayload.id(channel),\n                (payload, context) -> dispatcher.receiveClient(channel, payload.toBuffer()));', required=False)
replace(path,
        'ClientPlayNetworking.registerGlobalReceiver(chunk, (client, handler, buf, responseSender) -> dispatcher.receiveClient(chunk, buf));',
        'RawPayload.registerS2C(chunk);\n        ClientPlayNetworking.registerGlobalReceiver(RawPayload.id(chunk),\n            (payload, context) -> dispatcher.receiveClient(chunk, payload.toBuffer()));', required=False)

path = 'src/main/java/mchorse/blockbuster/Blockbuster.java'
add_import(path, 'import mchorse.mclib.network.ChannelLedger;\n', 'import mchorse.mclib.network.RawPayload;\n')
add_import(path, 'import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;\n',
           'import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;\n')
replace(path, 'sender.sendPacket(ChannelLedger.HANDSHAKE, buf);',
        'ServerPlayNetworking.send(handler.player, RawPayload.of(ChannelLedger.HANDSHAKE, buf));', required=False)
text = read(path)
join_needle = '        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->\n'
if join_needle in text and 'RawPayload.registerS2C(ChannelLedger.HANDSHAKE);' not in text:
    write(path, text.replace(join_needle,
        '        RawPayload.registerS2C(ChannelLedger.HANDSHAKE);\n' + join_needle))

path = 'src/client/java/mchorse/blockbuster/BlockbusterClient.java'
add_import(path, 'import mchorse.mclib.network.ClientDispatcherHooks;\n', 'import mchorse.mclib.network.RawPayload;\n')
replace(path,
        'ClientPlayNetworking.registerGlobalReceiver(ChannelLedger.HANDSHAKE,\n            (client, handler, buf, responseSender) -> ClientNetworkState.onHandshake(buf));',
        'RawPayload.registerS2C(ChannelLedger.HANDSHAKE);\n        ClientPlayNetworking.registerGlobalReceiver(RawPayload.id(ChannelLedger.HANDSHAKE),\n            (payload, context) -> ClientNetworkState.onHandshake(payload.toBuffer()));', required=False)

path = 'src/client/java/mchorse/metamorph/client/NetworkHandler.java'
add_import(path, 'import mchorse.metamorph.capabilities.morphing.Morphing;\n', 'import mchorse.mclib.network.RawPayload;\n')
old = '''        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, responseSender) ->\n        {\n'''
new = '''        RawPayload.registerS2C(CHANNEL);\n        ClientPlayNetworking.registerGlobalReceiver(RawPayload.id(CHANNEL), (payload, context) ->\n        {\n            MinecraftClient client = context.client();\n            PacketByteBuf buf = payload.toBuffer();\n'''
replace(path, old, new, required=False)

print('Applied remaining Minecraft 1.21.1 common-source migration fixes.')
