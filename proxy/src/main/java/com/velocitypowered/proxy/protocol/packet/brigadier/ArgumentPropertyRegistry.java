/*
 * Copyright (C) 2018 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.protocol.packet.brigadier;

import static com.velocitypowered.proxy.protocol.packet.brigadier.DoubleArgumentPropertySerializer.DOUBLE;
import static com.velocitypowered.proxy.protocol.packet.brigadier.EmptyArgumentPropertySerializer.EMPTY;
import static com.velocitypowered.proxy.protocol.packet.brigadier.FloatArgumentPropertySerializer.FLOAT;
import static com.velocitypowered.proxy.protocol.packet.brigadier.IntegerArgumentPropertySerializer.INTEGER;
import static com.velocitypowered.proxy.protocol.packet.brigadier.LongArgumentPropertySerializer.LONG;
import static com.velocitypowered.proxy.protocol.packet.brigadier.ModArgumentPropertySerializer.MOD;
import static com.velocitypowered.proxy.protocol.packet.brigadier.StringArgumentPropertySerializer.STRING;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ArgumentPropertyRegistry {
  private ArgumentPropertyRegistry() {
    throw new AssertionError();
  }

  private static final Map<String, ArgumentPropertySerializer<?>> byId = new HashMap<>();
  private static final Int2ObjectMap<ArgumentPropertySerializer<?>> byIntId = new Int2ObjectOpenHashMap<>();
  private static final Map<Class<? extends ArgumentType>,
      ArgumentPropertySerializer<?>> byClass = new HashMap<>();
  private static final Int2ObjectMap<String> intIdToId = new Int2ObjectOpenHashMap<>();
  private static final Map<Class<? extends ArgumentType>, String> classToId = new HashMap<>();
  private static final Object2IntMap<Class<? extends ArgumentType>> classToIntId = new Object2IntOpenHashMap<>();

  private static <T extends ArgumentType<?>> void register(String identifier, Class<T> klazz,
      ArgumentPropertySerializer<T> serializer) {
    register(-1, identifier, klazz, serializer);
  }

  private static <T extends ArgumentType<?>> void register(int id, String identifier,
      Class<T> klazz, ArgumentPropertySerializer<T> serializer) {
    byId.put(identifier, serializer);
    byClass.put(klazz, serializer);
    classToId.put(klazz, identifier);
    if (id != -1) {
      byIntId.put(id, serializer);
      intIdToId.put(id, identifier);
      classToIntId.put(klazz, id);
    }
  }

  private static <T> void empty(String identifier) {
    empty(-1, identifier);
  }

  private static <T> void empty(int id, String identifier) {
    empty(id, identifier, EMPTY);
  }

  private static <T> void empty(int id, String identifier, ArgumentPropertySerializer<T> serializer) {
    byId.put(identifier, serializer);
    if (id != -1) {
      byIntId.put(id, serializer);
    }
  }

  /**
   * Deserializes the {@link ArgumentType}.
   * @param buf the buffer to deserialize
   * @param version client protocol version
   * @return the deserialized {@link ArgumentType}
   */
  public static ArgumentType<?> deserialize(ByteBuf buf, ProtocolVersion version) {
    int intId;
    String identifier;
    ArgumentPropertySerializer<?> serializer;
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      intId = ProtocolUtils.readVarInt(buf);
      identifier = intIdToId.get(intId);
      serializer = byIntId.get(intId);
    } else {
      identifier = ProtocolUtils.readString(buf);
      serializer = byId.get(identifier);
      intId = classToIntId.getInt(serializer);
    }
    if (serializer == null) {
      throw new IllegalArgumentException("Argument type identifier " + identifier + " unknown. (" + intId + ")");
    }
    Object result = serializer.deserialize(buf);

    if (result instanceof ArgumentType) {
      return (ArgumentType<?>) result;
    } else {
      return new PassthroughProperty(intId, identifier, serializer, result);
    }
  }

  /**
   * Serializes the {@code type} into the provided {@code buf}.
   * @param buf the buffer to serialize into
   * @param type the type to serialize
   * @param version client protocol version
   */
  public static void serialize(ByteBuf buf, ArgumentType<?> type, ProtocolVersion version) {
    if (type instanceof PassthroughProperty) {
      PassthroughProperty property = (PassthroughProperty) type;
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
        ProtocolUtils.writeVarInt(buf, property.getIntId());
      } else {
        ProtocolUtils.writeString(buf, property.getIdentifier());
      }
      if (property.getResult() != null) {
        property.getSerializer().serialize(property.getResult(), buf);
      }
    } else if (type instanceof ModArgumentProperty) {
      ModArgumentProperty property = (ModArgumentProperty) type;
      ProtocolUtils.writeString(buf, property.getIdentifier());
      buf.writeBytes(property.getData());
    } else {
      ArgumentPropertySerializer serializer = byClass.get(type.getClass());
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
        int id = classToIntId.getInt(type.getClass());
        if (serializer == null || id == -1) {
          throw new IllegalArgumentException("Don't know how to serialize "
              + type.getClass().getName() + " (ID: " + id + ")");
        }
        ProtocolUtils.writeVarInt(buf, id);
      } else {
        String id = classToId.get(type.getClass());
        if (serializer == null || id == null) {
          throw new IllegalArgumentException("Don't know how to serialize "
              + type.getClass().getName());
        }
        ProtocolUtils.writeString(buf, id);
      }
      serializer.serialize(type, buf);
    }
  }

  static {
    classToIntId.defaultReturnValue(-1);
    // Base Brigadier argument types
    register(5, "brigadier:string", StringArgumentType.class, STRING);
    register(3, "brigadier:integer", IntegerArgumentType.class, INTEGER);
    register(1, "brigadier:float", FloatArgumentType.class, FLOAT);
    register(2, "brigadier:double", DoubleArgumentType.class, DOUBLE);
    register(0, "brigadier:bool", BoolArgumentType.class,
        new ArgumentPropertySerializer<>() {
          @Override
          public BoolArgumentType deserialize(ByteBuf buf) {
            return BoolArgumentType.bool();
          }

          @Override
          public void serialize(BoolArgumentType object, ByteBuf buf) {

          }
        });
    register(4, "brigadier:long", LongArgumentType.class, LONG);
    register(44, "minecraft:resource", RegistryKeyArgument.class, RegistryKeyArgumentSerializer.REGISTRY);
    register(43, "minecraft:resource_or_tag", RegistryKeyArgument.class, RegistryKeyArgumentSerializer.REGISTRY);

    // Crossstitch support
    register("crossstitch:mod_argument", ModArgumentProperty.class, MOD);

    // Minecraft argument types with extra properties
    empty(6, "minecraft:entity", ByteArgumentPropertySerializer.BYTE);
    empty(29, "minecraft:score_holder", ByteArgumentPropertySerializer.BYTE);

    // Minecraft argument types
    empty(7, "minecraft:game_profile");
    empty(8, "minecraft:block_pos");
    empty(9, "minecraft:column_pos");
    empty(10, "minecraft:vec3");
    empty(11, "minecraft:vec2");
    empty(12, "minecraft:block_state");
    empty(13, "minecraft:block_predicate");
    empty(14, "minecraft:item_stack");
    empty(15, "minecraft:item_predicate");
    empty(16, "minecraft:color");
    empty(17, "minecraft:component");
    empty(18, "minecraft:message");
    empty("minecraft:nbt");
    empty(19, "minecraft:nbt_compound_tag"); // added in 1.14
    empty(20, "minecraft:nbt_tag"); // added in 1.14
    empty(21, "minecraft:nbt_path");
    empty(22, "minecraft:objective");
    empty(23, "minecraft:objective_criteria");
    empty(24, "minecraft:operation");
    empty(25, "minecraft:particle");
    empty(27, "minecraft:rotation");
    empty(28, "minecraft:scoreboard_slot");
    empty(30, "minecraft:swizzle");
    empty(31, "minecraft:team");
    empty(32, "minecraft:item_slot");
    empty(33, "minecraft:resource_location");
    empty(34, "minecraft:mob_effect");
    empty(35, "minecraft:function");
    empty(36, "minecraft:entity_anchor");
    empty(39, "minecraft:item_enchantment");
    empty(40, "minecraft:entity_summon");
    empty(41, "minecraft:dimension");
    empty(37, "minecraft:int_range");
    empty(38, "minecraft:float_range");
    empty(42, "minecraft:time"); // added in 1.14
    empty(47, "minecraft:uuid"); // added in 1.16
    empty(26, "minecraft:angle"); // added in 1.16.2
    empty(45, "minecraft:template_mirror"); // added in 1.19
    empty(46, "minecraft:template_rotation"); // added in 1.19
  }
}
