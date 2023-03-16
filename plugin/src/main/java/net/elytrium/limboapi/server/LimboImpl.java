/*
 * Copyright (C) 2021 - 2023 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboapi.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.command.registrar.BrigadierCommandRegistrar;
import com.velocitypowered.proxy.command.registrar.CommandRegistrar;
import com.velocitypowered.proxy.command.registrar.RawCommandRegistrar;
import com.velocitypowered.proxy.command.registrar.SimpleCommandRegistrar;
import com.velocitypowered.proxy.connection.ConnectionTypes;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.registry.DimensionData;
import com.velocitypowered.proxy.connection.registry.DimensionInfo;
import com.velocitypowered.proxy.connection.registry.DimensionRegistry;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.VelocityConnectionEvent;
import com.velocitypowered.proxy.protocol.packet.AvailableCommands;
import com.velocitypowered.proxy.protocol.packet.JoinGame;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItem;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import com.velocitypowered.proxy.protocol.packet.Respawn;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.elytrium.commons.utils.reflection.ReflectionException;
import net.elytrium.fastprepare.PreparedPacketFactory;
import net.elytrium.limboapi.LimboAPI;
import net.elytrium.limboapi.Settings;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualChunk;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.command.LimboCommandMeta;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.injection.packet.MinecraftLimitedCompressDecoder;
import net.elytrium.limboapi.material.Biome;
import net.elytrium.limboapi.protocol.LimboProtocol;
import net.elytrium.limboapi.protocol.packets.s2c.ChunkDataPacket;
import net.elytrium.limboapi.protocol.packets.s2c.DefaultSpawnPositionPacket;
import net.elytrium.limboapi.protocol.packets.s2c.PositionRotationPacket;
import net.elytrium.limboapi.protocol.packets.s2c.TimeUpdatePacket;
import net.elytrium.limboapi.protocol.packets.s2c.UpdateViewPositionPacket;
import net.elytrium.limboapi.server.world.SimpleTagManager;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.text.Component;

public class LimboImpl implements Limbo {

  private static final MethodHandle PARTIAL_HASHED_SEED_FIELD;
  private static final MethodHandle CURRENT_DIMENSION_DATA_FIELD;
  private static final MethodHandle ROOT_NODE_FIELD;
  private static final MethodHandle GRACEFUL_DISCONNECT_FIELD;
  private static final MethodHandle ORIGINAL_REGISTRY_CONTAINER_FIELD;

  private static final CompoundBinaryTag CHAT_TYPE_119;
  private static final CompoundBinaryTag CHAT_TYPE_1191;
  private static final CompoundBinaryTag DAMAGE_TYPE_1194;

  private final Map<Class<? extends LimboSessionHandler>, PreparedPacket> brandMessages = new HashMap<>();
  private final LimboAPI plugin;
  private final VirtualWorld world;

  private final LongAdder currentOnline = new LongAdder();
  private final RootCommandNode<CommandSource> commandNode = new RootCommandNode<>();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final List<CommandRegistrar<?>> registrars = ImmutableList.of(
      new BrigadierCommandRegistrar(this.commandNode, this.lock.writeLock()),
      new SimpleCommandRegistrar(this.commandNode, this.lock.writeLock()),
      new RawCommandRegistrar(this.commandNode, this.lock.writeLock())
  );

  private String limboName;
  private Integer readTimeout;
  private Long worldTicks;
  private short gameMode = GameMode.ADVENTURE.getID();
  private Integer maxSuppressPacketLength;

  private PreparedPacket joinPackets;
  private PreparedPacket fastRejoinPackets;
  private PreparedPacket safeRejoinPackets;
  private PreparedPacket postJoinPackets;
  private PreparedPacket firstChunks;
  private List<PreparedPacket> delayedChunks;
  private PreparedPacket respawnPackets;
  private boolean shouldRespawn = true;
  private boolean shouldRejoin = true;
  private boolean shouldUpdateTags = true;
  private boolean reducedDebugInfo = Settings.IMP.MAIN.REDUCED_DEBUG_INFO;
  private int viewDistance = Settings.IMP.MAIN.VIEW_DISTANCE;
  private int simulationDistance = Settings.IMP.MAIN.SIMULATION_DISTANCE;
  private boolean built = true;
  private boolean disposeScheduled = false;

  public LimboImpl(LimboAPI plugin, VirtualWorld world) {
    this.plugin = plugin;
    this.world = world;

    this.refresh();
  }

  protected void refresh() {
    this.built = true;
    JoinGame legacyJoinGame = this.createLegacyJoinGamePacket();
    JoinGame joinGame = this.createJoinGamePacket(ProtocolVersion.MINECRAFT_1_16);
    JoinGame joinGame1162 = this.createJoinGamePacket(ProtocolVersion.MINECRAFT_1_16_2);
    JoinGame joinGameModern = this.createJoinGamePacket(ProtocolVersion.MINECRAFT_1_18_2);
    JoinGame joinGame1191 = this.createJoinGamePacket(ProtocolVersion.MINECRAFT_1_19_1);
    JoinGame joinGame1194 = this.createJoinGamePacket(ProtocolVersion.MINECRAFT_1_19_4);

    this.joinPackets = this.plugin.createPreparedPacket()
        .prepare(legacyJoinGame, ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_15_2)
        .prepare(joinGame, ProtocolVersion.MINECRAFT_1_16, ProtocolVersion.MINECRAFT_1_16_1)
        .prepare(joinGame1162, ProtocolVersion.MINECRAFT_1_16_2, ProtocolVersion.MINECRAFT_1_18)
        .prepare(joinGameModern, ProtocolVersion.MINECRAFT_1_18_2, ProtocolVersion.MINECRAFT_1_19)
        .prepare(joinGame1191, ProtocolVersion.MINECRAFT_1_19_1, ProtocolVersion.MINECRAFT_1_19_3)
        .prepare(joinGame1194, ProtocolVersion.MINECRAFT_1_19_4);

    this.fastRejoinPackets = this.plugin.createPreparedPacket();
    this.createFastClientServerSwitch(legacyJoinGame, ProtocolVersion.MINECRAFT_1_7_2)
        .forEach(minecraftPacket -> this.fastRejoinPackets.prepare(minecraftPacket, ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_15_2));
    this.createFastClientServerSwitch(joinGame, ProtocolVersion.MINECRAFT_1_16)
        .forEach(minecraftPacket -> this.fastRejoinPackets.prepare(minecraftPacket, ProtocolVersion.MINECRAFT_1_16, ProtocolVersion.MINECRAFT_1_16_1));
    this.createFastClientServerSwitch(joinGame1162, ProtocolVersion.MINECRAFT_1_16_2)
        .forEach(minecraftPacket -> this.fastRejoinPackets.prepare(minecraftPacket, ProtocolVersion.MINECRAFT_1_16_2, ProtocolVersion.MINECRAFT_1_18));
    this.createFastClientServerSwitch(joinGameModern, ProtocolVersion.MINECRAFT_1_18_2)
        .forEach(minecraftPacket -> this.fastRejoinPackets.prepare(minecraftPacket, ProtocolVersion.MINECRAFT_1_18_2, ProtocolVersion.MINECRAFT_1_19));
    this.createFastClientServerSwitch(joinGame1191, ProtocolVersion.MINECRAFT_1_19_1)
        .forEach(minecraftPacket -> this.fastRejoinPackets.prepare(minecraftPacket, ProtocolVersion.MINECRAFT_1_19_1, ProtocolVersion.MINECRAFT_1_19_3));
    this.createFastClientServerSwitch(joinGame1194, ProtocolVersion.MINECRAFT_1_19_4)
        .forEach(minecraftPacket -> this.fastRejoinPackets.prepare(minecraftPacket, ProtocolVersion.MINECRAFT_1_19_4));

    this.safeRejoinPackets = this.plugin.createPreparedPacket().prepare(this.createSafeClientServerSwitch(legacyJoinGame));
    this.postJoinPackets = this.plugin.createPreparedPacket();

    this.addPostJoin(this.joinPackets);
    this.addPostJoin(this.fastRejoinPackets);
    this.addPostJoin(this.safeRejoinPackets);
    this.addPostJoin(this.postJoinPackets);

    this.firstChunks = this.createFirstChunks();
    this.delayedChunks = this.createDelayedChunksPackets();
    this.respawnPackets = this.plugin.createPreparedPacket()
        .prepare(
            this.createPlayerPosAndLook(
                this.world.getSpawnX(), this.world.getSpawnY(), this.world.getSpawnZ(), this.world.getYaw(), this.world.getPitch()
            )
        ).prepare(
            this.createUpdateViewPosition((int) this.world.getSpawnX(), (int) this.world.getSpawnZ()),
            ProtocolVersion.MINECRAFT_1_14
        );

    if (this.shouldUpdateTags) {
      this.respawnPackets.prepare(SimpleTagManager::getUpdateTagsPacket, ProtocolVersion.MINECRAFT_1_13);
    }

    this.respawnPackets.build();
  }

  private void addPostJoin(PreparedPacket packet) {
    packet.prepare(this.createAvailableCommandsPacket(), ProtocolVersion.MINECRAFT_1_13)
        .prepare(this.createDefaultSpawnPositionPacket())
        .prepare(this.createWorldTicksPacket())
        .prepare(this::createBrandMessage)
        .build();
  }

  @Override
  public void spawnPlayer(Player apiPlayer, LimboSessionHandler handler) {
    if (!this.built) {
      this.refresh();
    }

    ConnectedPlayer player = (ConnectedPlayer) apiPlayer;
    MinecraftConnection connection = player.getConnection();

    boolean shouldSpawnPlayerImmediately = true;

    if (connection.getState() != LimboProtocol.getLimboStateRegistry()) {
      connection.eventLoop().execute(() -> connection.setState(LimboProtocol.getLimboStateRegistry()));
      VelocityServerConnection server = player.getConnectedServer();
      if (server != null) {
        RegisteredServer previousServer = server.getServer();
        MinecraftConnection serverConnection = server.getConnection();
        if (serverConnection != null) {
          try {
            GRACEFUL_DISCONNECT_FIELD.invokeExact(server, true);
          } catch (Throwable e) {
            throw new ReflectionException(e);
          }

          connection.eventLoop().execute(() ->
              serverConnection.getChannel().close().addListener(f -> this.spawnPlayerLocal(player, handler, previousServer)));
          shouldSpawnPlayerImmediately = false;
        }

        player.setConnectedServer(null);
        this.plugin.setLimboJoined(player);
      }
    }

    if (shouldSpawnPlayerImmediately) {
      this.spawnPlayerLocal(player, handler, null);
    }
  }

  private void spawnPlayerLocal(ConnectedPlayer player, LimboSessionHandler handler, RegisteredServer previousServer) {
    MinecraftConnection connection = player.getConnection();
    connection.eventLoop().execute(() -> {
      connection.flush();

      ChannelPipeline pipeline = connection.getChannel().pipeline();
      Class<? extends LimboSessionHandler> handlerClass = handler.getClass();
      if (this.limboName == null) {
        this.limboName = handlerClass.getSimpleName();
      }

      if (Settings.IMP.MAIN.LOGGING_ENABLED) {
        LimboAPI.getLogger().info(player.getUsername() + " (" + player.getRemoteAddress() + ") has connected to the " + this.limboName + " Limbo");
      }

      // With an abnormally large number of connections from the same nickname,
      // requests don't have time to be processed, and an error occurs that "minecraft-encoder" doesn't exist.
      if (pipeline.get(Connections.MINECRAFT_ENCODER) != null) {
        if (this.readTimeout != null) {
          pipeline.replace(Connections.READ_TIMEOUT, LimboProtocol.READ_TIMEOUT, new ReadTimeoutHandler(this.readTimeout, TimeUnit.MILLISECONDS));
        }

        boolean compressionEnabled = false;

        if (pipeline.get(PreparedPacketFactory.PREPARED_ENCODER) == null) {
          if (this.plugin.isCompressionEnabled() && connection.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
            if (pipeline.get(Connections.FRAME_ENCODER) != null) {
              pipeline.remove(Connections.FRAME_ENCODER);
            } else {
              ChannelHandler minecraftCompressDecoder = pipeline.remove(Connections.COMPRESSION_DECODER);
              if (minecraftCompressDecoder != null) {
                this.plugin.fixDecompressor(pipeline, this.plugin.getServer().getConfiguration().getCompressionThreshold(), false);
                pipeline.replace(Connections.COMPRESSION_ENCODER, Connections.COMPRESSION_ENCODER, new ChannelOutboundHandlerAdapter());
                compressionEnabled = true;
              }
            }
          } else {
            pipeline.remove(Connections.FRAME_ENCODER);
          }

          this.plugin.inject3rdParty(player, connection, pipeline);
          if (compressionEnabled) {
            pipeline.fireUserEventTriggered(VelocityConnectionEvent.COMPRESSION_ENABLED);
          }
        }
      } else {
        connection.close();
        return;
      }

      if (this.maxSuppressPacketLength != null) {
        MinecraftLimitedCompressDecoder decoder = pipeline.get(MinecraftLimitedCompressDecoder.class);
        if (decoder != null) {
          decoder.setUncompressedCap(this.maxSuppressPacketLength);
        }
      }

      if (this.plugin.isLimboJoined(player)) {
        if (this.shouldRejoin) {
          if (connection.getType() == ConnectionTypes.LEGACY_FORGE) {
            connection.delayedWrite(this.safeRejoinPackets);
          } else {
            connection.delayedWrite(this.fastRejoinPackets);
          }
        } else {
          connection.delayedWrite(this.postJoinPackets);
        }
      } else {
        connection.delayedWrite(this.joinPackets);
      }

      MinecraftPacket playerInfoPacket;

      if (connection.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_19_1) <= 0) {
        playerInfoPacket = new LegacyPlayerListItem(
            LegacyPlayerListItem.ADD_PLAYER,
            List.of(
                new LegacyPlayerListItem.Item(player.getUniqueId())
                    .setName(player.getUsername())
                    .setGameMode(this.gameMode)
                    .setProperties(player.getGameProfileProperties())
            )
        );
      } else {
        UpsertPlayerInfo.Entry playerInfoEntry = new UpsertPlayerInfo.Entry(player.getUniqueId());
        playerInfoEntry.setDisplayName(Component.text(player.getUsername()));
        playerInfoEntry.setGameMode(this.gameMode);
        playerInfoEntry.setProfile(player.getGameProfile());

        playerInfoPacket = new UpsertPlayerInfo(
            EnumSet.of(
                UpsertPlayerInfo.Action.UPDATE_DISPLAY_NAME,
                UpsertPlayerInfo.Action.UPDATE_GAME_MODE,
                UpsertPlayerInfo.Action.ADD_PLAYER),
            List.of(playerInfoEntry));
      }

      connection.delayedWrite(playerInfoPacket);

      connection.delayedWrite(this.getBrandMessage(handlerClass));

      this.plugin.setLimboJoined(player);

      LimboSessionHandlerImpl sessionHandler = new LimboSessionHandlerImpl(
          this.plugin,
          this,
          player,
          handler,
          connection.getSessionHandler(),
          previousServer,
          () -> this.limboName
      );
      connection.setSessionHandler(sessionHandler);

      connection.flush();

      if (this.shouldRespawn) {
        this.respawnPlayer(player);
      }

      this.currentOnline.increment();
      sessionHandler.onSpawn(new LimboPlayerImpl(this.plugin, this, player));
    });
  }

  @Override
  public void respawnPlayer(Player player) {
    MinecraftConnection connection = ((ConnectedPlayer) player).getConnection();

    connection.delayedWrite(this.respawnPackets);
    if (this.firstChunks != null) {
      connection.write(this.firstChunks);
    }

    int packetIndex = 0;
    for (PreparedPacket chunk : this.delayedChunks) {
      connection.eventLoop().schedule(() -> connection.write(chunk), (++packetIndex) * 50L, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public long getCurrentOnline() {
    return this.currentOnline.sum();
  }

  public void onDisconnect() {
    this.currentOnline.decrement();

    if (this.disposeScheduled && this.currentOnline.sum() == 0) {
      this.localDispose();
    }
  }

  @Override
  public Limbo setName(String name) {
    this.limboName = name;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setReadTimeout(int millis) {
    this.readTimeout = millis;
    return this;
  }

  @Override
  public Limbo setWorldTime(long ticks) {
    this.worldTicks = ticks;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setGameMode(GameMode gameMode) {
    this.gameMode = gameMode.getID();

    this.built = false;
    return this;
  }

  @Override
  public Limbo setShouldRejoin(boolean shouldRejoin) {
    this.shouldRejoin = shouldRejoin;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setShouldRespawn(boolean shouldRespawn) {
    this.shouldRespawn = shouldRespawn;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setShouldUpdateTags(boolean shouldUpdateTags) {
    this.shouldUpdateTags = shouldUpdateTags;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setReducedDebugInfo(boolean reducedDebugInfo) {
    this.reducedDebugInfo = reducedDebugInfo;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setViewDistance(int viewDistance) {
    this.viewDistance = viewDistance;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setSimulationDistance(int simulationDistance) {
    this.simulationDistance = simulationDistance;

    this.built = false;
    return this;
  }

  @Override
  public Limbo setMaxSuppressPacketLength(int maxSuppressPacketLength) {
    this.maxSuppressPacketLength = maxSuppressPacketLength;

    return this;
  }

  @Override
  public Limbo registerCommand(LimboCommandMeta commandMeta) {
    return this.registerCommand(commandMeta, (SimpleCommand) invocation -> {
      // Do nothing.
    });
  }

  @Override
  public Limbo registerCommand(CommandMeta commandMeta, Command command) {
    for (CommandRegistrar<?> registrar : this.registrars) {
      if (this.tryRegister(registrar, commandMeta, command)) {
        this.built = false;
        return this;
      }
    }

    throw new IllegalArgumentException(command + " does not implement a registrable Command sub-interface.");
  }

  @Override
  public void dispose() {
    if (this.getCurrentOnline() == 0) {
      this.localDispose();
    } else {
      this.disposeScheduled = true;
    }
  }

  private void localDispose() {
    this.joinPackets.release();
    this.fastRejoinPackets.release();
    this.safeRejoinPackets.release();
    this.respawnPackets.release();
    this.firstChunks.release();
    this.delayedChunks.forEach(PreparedPacket::release);
  }

  // From Velocity.
  private <T extends Command> boolean tryRegister(CommandRegistrar<T> registrar, CommandMeta commandMeta, Command command) {
    Class<T> superInterface = registrar.registrableSuperInterface();
    if (superInterface.isInstance(command)) {
      registrar.register(commandMeta, superInterface.cast(command));
      return true;
    } else {
      return false;
    }
  }

  private DimensionData createDimensionData(Dimension dimension, ProtocolVersion version) {
    return new DimensionData(
        dimension.getKey(), dimension.getModernID(), false,
        0.0F, false, false, false, true,
        false, false, false, false, 256,
        version.compareTo(ProtocolVersion.MINECRAFT_1_18_2) >= 0 ? "#minecraft:infiniburn_nether" : "minecraft:infiniburn_nether",
        null, null, 1.0, dimension.getKey(), 0, 256,
        0, 0
    );
  }

  private DimensionRegistry createDimensionRegistry(ProtocolVersion version) {
    return new DimensionRegistry(
        ImmutableSet.of(
            this.createDimensionData(Dimension.OVERWORLD, version),
            this.createDimensionData(Dimension.NETHER, version),
            this.createDimensionData(Dimension.THE_END, version)
        ),
        ImmutableSet.of(
            Dimension.OVERWORLD.getKey(),
            Dimension.NETHER.getKey(),
            Dimension.THE_END.getKey()
        )
    );
  }

  private JoinGame createJoinGamePacket(ProtocolVersion version) {
    Dimension dimension = this.world.getDimension();
    JoinGame joinGame = new JoinGame();
    joinGame.setEntityId(1);
    joinGame.setIsHardcore(true);
    joinGame.setGamemode(this.gameMode);
    joinGame.setPreviousGamemode((short) -1);
    joinGame.setDimension(dimension.getModernID());
    joinGame.setDifficulty((short) 0);
    try {
      PARTIAL_HASHED_SEED_FIELD.invokeExact(joinGame, ThreadLocalRandom.current().nextLong());
    } catch (Throwable e) {
      throw new ReflectionException(e);
    }
    joinGame.setMaxPlayers(1);

    joinGame.setLevelType("flat");

    joinGame.setViewDistance(this.viewDistance);
    joinGame.setSimulationDistance(this.simulationDistance);

    joinGame.setReducedDebugInfo(this.reducedDebugInfo);

    String key = dimension.getKey();
    DimensionRegistry dimensionRegistry = this.createDimensionRegistry(version);
    joinGame.setDimensionRegistry(dimensionRegistry);
    joinGame.setDimensionInfo(new DimensionInfo(key, key, false, false));

    CompoundBinaryTag.Builder registryContainer = CompoundBinaryTag.builder();
    ListBinaryTag encodedDimensionRegistry = dimensionRegistry.encodeRegistry(version);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_16_2) >= 0) {
      CompoundBinaryTag.Builder dimensionRegistryEntry = CompoundBinaryTag.builder();
      dimensionRegistryEntry.putString("type", "minecraft:dimension_type");
      dimensionRegistryEntry.put("value", encodedDimensionRegistry);
      registryContainer.put("minecraft:dimension_type", dimensionRegistryEntry.build());
      registryContainer.put("minecraft:worldgen/biome", Biome.getRegistry(version));
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) == 0) {
        registryContainer.put("minecraft:chat_type", CHAT_TYPE_119);
      } else if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_1) >= 0) {
        registryContainer.put("minecraft:chat_type", CHAT_TYPE_1191);
      }

      if (version.compareTo(ProtocolVersion.MINECRAFT_1_19_4) >= 0) {
        registryContainer.put("minecraft:damage_type", DAMAGE_TYPE_1194);
      }
    } else {
      registryContainer.put("dimension", encodedDimensionRegistry);
    }

    try {
      CURRENT_DIMENSION_DATA_FIELD.invokeExact(joinGame, dimensionRegistry.getDimensionData(dimension.getKey()));
      ORIGINAL_REGISTRY_CONTAINER_FIELD.invokeExact(joinGame, registryContainer.build());
    } catch (Throwable e) {
      throw new ReflectionException(e);
    }

    return joinGame;
  }

  private JoinGame createLegacyJoinGamePacket() {
    JoinGame joinGame = this.createJoinGamePacket(ProtocolVersion.MINIMUM_VERSION);
    joinGame.setDimension(this.world.getDimension().getLegacyID());
    return joinGame;
  }

  private DefaultSpawnPositionPacket createDefaultSpawnPositionPacket() {
    return new DefaultSpawnPositionPacket((int) this.world.getSpawnX(), (int) this.world.getSpawnY(), (int) this.world.getSpawnZ(), 0.0F);
  }

  private TimeUpdatePacket createWorldTicksPacket() {
    return this.worldTicks == null ? null : new TimeUpdatePacket(this.worldTicks, this.worldTicks);
  }

  private AvailableCommands createAvailableCommandsPacket() {
    try {
      AvailableCommands packet = new AvailableCommands();
      ROOT_NODE_FIELD.invokeExact(packet, this.commandNode);
      return packet;
    } catch (Throwable e) {
      throw new ReflectionException(e);
    }
  }

  private PreparedPacket createFirstChunks() {
    PreparedPacket packet = this.plugin.createPreparedPacket();
    List<List<VirtualChunk>> orderedChunks = this.world.getOrderedChunks();

    int chunkCounter = 0;
    for (List<VirtualChunk> chunksWithSameDistance : orderedChunks) {
      if (++chunkCounter > Settings.IMP.MAIN.CHUNK_RADIUS_SEND_ON_SPAWN) {
        break;
      }

      for (VirtualChunk chunk : chunksWithSameDistance) {
        packet.prepare(this.createChunkData(chunk, this.world.getDimension()));
      }
    }

    return packet.build();
  }

  private List<PreparedPacket> createDelayedChunksPackets() {
    List<List<VirtualChunk>> orderedChunks = this.world.getOrderedChunks();
    if (orderedChunks.size() <= Settings.IMP.MAIN.CHUNK_RADIUS_SEND_ON_SPAWN) {
      return List.of();
    }

    List<PreparedPacket> packets = new LinkedList<>();
    PreparedPacket packet = this.plugin.createPreparedPacket();
    int chunkCounter = 0;

    Iterator<List<VirtualChunk>> distanceIterator = orderedChunks.listIterator();
    for (int i = 0; i < Settings.IMP.MAIN.CHUNK_RADIUS_SEND_ON_SPAWN; i++) {
      distanceIterator.next();
    }

    while (distanceIterator.hasNext()) {
      for (VirtualChunk chunk : distanceIterator.next()) {
        if (++chunkCounter > Settings.IMP.MAIN.CHUNKS_PER_TICK) {
          packets.add(packet.build());
          packet = this.plugin.createPreparedPacket();
          chunkCounter = 0;
        }

        packet.prepare(this.createChunkData(chunk, this.world.getDimension()));
      }
    }

    packets.add(packet.build());

    return packets;
  }

  // From Velocity.
  private List<MinecraftPacket> createFastClientServerSwitch(JoinGame joinGame, ProtocolVersion version) {
    // In order to handle switching to another server, you will need to send two packets:
    //
    // - The join game packet from the backend server, with a different dimension.
    // - A respawn with the correct dimension.
    //
    // Most notably, by having the client accept the join game packet, we can work around the need
    // to perform entity ID rewrites, eliminating potential issues from rewriting packets and
    // improving compatibility with mods.
    List<MinecraftPacket> packets = new ArrayList<>();

    Respawn respawn = Respawn.fromJoinGame(joinGame);

    if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) < 0) {
      // Before Minecraft 1.16, we could not switch to the same dimension without sending an
      // additional respawn. On older versions of Minecraft this forces the client to perform
      // garbage collection which adds additional latency.
      joinGame.setDimension(joinGame.getDimension() == 0 ? -1 : 0);
    }

    packets.add(joinGame);
    packets.add(respawn);

    return packets;
  }

  private List<MinecraftPacket> createSafeClientServerSwitch(JoinGame joinGame) {
    // Some clients do not behave well with the "fast" respawn sequence. In this case we will use
    // a "safe" respawn sequence that involves sending three packets to the client. They have the
    // same effect but tend to work better with buggier clients (Forge 1.8 in particular).
    List<MinecraftPacket> packets = new ArrayList<>();

    // Send the JoinGame packet itself, unmodified.
    packets.add(joinGame);

    // Send a respawn packet in a different dimension.
    Respawn fakeSwitchPacket = Respawn.fromJoinGame(joinGame);
    fakeSwitchPacket.setDimension(joinGame.getDimension() == 0 ? -1 : 0);
    packets.add(fakeSwitchPacket);

    // Now send a respawn packet in the correct dimension.
    Respawn correctSwitchPacket = Respawn.fromJoinGame(joinGame);
    packets.add(correctSwitchPacket);

    return packets;
  }

  private PreparedPacket getBrandMessage(Class<? extends LimboSessionHandler> handlerClass) {
    if (this.brandMessages.containsKey(handlerClass)) {
      return this.brandMessages.get(handlerClass);
    } else {
      PreparedPacket preparedPacket = this.plugin.createPreparedPacket().prepare(this::createBrandMessage).build();
      this.brandMessages.put(handlerClass, preparedPacket);
      return preparedPacket;
    }
  }

  private PluginMessage createBrandMessage(ProtocolVersion version) {
    String brand = "LimboAPI (" + Settings.IMP.VERSION + ") -> " + this.limboName;
    ByteBuf bufWithBrandString = Unpooled.buffer();
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) < 0) {
      bufWithBrandString.writeCharSequence(brand, StandardCharsets.UTF_8);
    } else {
      ProtocolUtils.writeString(bufWithBrandString, brand);
    }

    return new PluginMessage("MC|Brand", bufWithBrandString);
  }

  private PositionRotationPacket createPlayerPosAndLook(double posX, double posY, double posZ, float yaw, float pitch) {
    return new PositionRotationPacket(posX, posY, posZ, yaw, pitch, false, 44, true);
  }

  private UpdateViewPositionPacket createUpdateViewPosition(int posX, int posZ) {
    return new UpdateViewPositionPacket(posX >> 4, posZ >> 4);
  }

  private ChunkDataPacket createChunkData(VirtualChunk chunk, Dimension dimension) {
    return new ChunkDataPacket(chunk.getFullChunkSnapshot(), dimension.hasLegacySkyLight(), dimension.getMaxSections());
  }

  public Integer getReadTimeout() {
    return this.readTimeout;
  }

  static {
    try {
      PARTIAL_HASHED_SEED_FIELD = MethodHandles.privateLookupIn(JoinGame.class, MethodHandles.lookup())
          .findSetter(JoinGame.class, "partialHashedSeed", long.class);
      CURRENT_DIMENSION_DATA_FIELD = MethodHandles.privateLookupIn(JoinGame.class, MethodHandles.lookup())
          .findSetter(JoinGame.class, "currentDimensionData", DimensionData.class);
      ROOT_NODE_FIELD = MethodHandles.privateLookupIn(AvailableCommands.class, MethodHandles.lookup())
          .findSetter(AvailableCommands.class, "rootNode", RootCommandNode.class);
      GRACEFUL_DISCONNECT_FIELD = MethodHandles.privateLookupIn(VelocityServerConnection.class, MethodHandles.lookup())
          .findSetter(VelocityServerConnection.class, "gracefulDisconnect", boolean.class);
      ORIGINAL_REGISTRY_CONTAINER_FIELD = MethodHandles.privateLookupIn(JoinGame.class, MethodHandles.lookup())
          .findSetter(JoinGame.class, "originalRegistryContainerTag", CompoundBinaryTag.class);
      try (InputStream stream = LimboAPI.class.getResourceAsStream("/mapping/chat_type_1_19.nbt")) {
        CHAT_TYPE_119 = BinaryTagIO.unlimitedReader().read(Objects.requireNonNull(stream), BinaryTagIO.Compression.GZIP);
      }
      try (InputStream stream = LimboAPI.class.getResourceAsStream("/mapping/chat_type_1_19_1.nbt")) {
        CHAT_TYPE_1191 = BinaryTagIO.unlimitedReader().read(Objects.requireNonNull(stream), BinaryTagIO.Compression.GZIP);
      }
      try (InputStream stream = LimboAPI.class.getResourceAsStream("/mapping/damage_type_1_19_4.nbt")) {
        DAMAGE_TYPE_1194 = BinaryTagIO.unlimitedReader().read(Objects.requireNonNull(stream), BinaryTagIO.Compression.GZIP);
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ReflectionException(e);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
