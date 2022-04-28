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

package com.velocitypowered.proxy.protocol.packet.chat;

import static com.velocitypowered.proxy.protocol.packet.chat.LegacyChatPacket.EMPTY_SENDER;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.EncryptionResponse;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class GenericChatPacket implements MinecraftPacket {

  public static final int MAX_SERVERBOUND_MESSAGE_LENGTH = 256;

  public static final byte CHAT_TYPE = (byte) 0;
  public static final byte SYSTEM_TYPE = (byte) 1;
  public static final byte GAME_INFO_TYPE = (byte) 2;

  protected byte type;
  protected @Nullable String message;

  /**
   * Get chat message.
   *
   * @return the chat message
   */
  public String getMessage() {
    if (message == null) {
      throw new IllegalStateException("Message is not specified");
    }
    return message;
  }

  /**
   * Set chat message.
   *
   * @param message the chat message
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Get message type.
   *
   * @return the message type
   */
  public byte getType() {
    return type;
  }

  /**
   * Set message type.
   *
   * @param type the message type
   */
  public void setType(byte type) {
    this.type = type;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    message = ProtocolUtils.readString(buf);
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (message == null) {
      throw new IllegalStateException("Message is not specified");
    }
    ProtocolUtils.writeString(buf, message);
  }

  /**
   * Create clientbound chat packet.
   *
   * @param identity the identity
   * @param component the chat component
   * @param version the protocol version
   * @return chat packet
   */
  public static GenericChatPacket createClientbound(Identity identity, Component component,
                      ProtocolVersion version) {
    return createClientbound(component, CHAT_TYPE, identity.uuid(), version);
  }

  /**
   * Create clientbound chat packet.
   *
   * @param component the chat component
   * @param type the message type
   * @param sender the chat sender
   * @param version the protocol version
   * @return chat packet
   */
  public static GenericChatPacket createClientbound(Component component, byte type,
                      UUID sender, ProtocolVersion version) {
    Preconditions.checkNotNull(component, "component");
    String serialized = ProtocolUtils.getJsonChatSerializer(version).serialize(component);
    return GenericChatPacket.createClientbound(serialized, type, sender, version);
  }

  /**
   * Create clientbound chat packet.
   *
   * @param component the chat component
   * @param type the message type
   * @param sender the chat sender
   * @param version the protocol version
   * @return chat packet
   */
  public static GenericChatPacket createClientbound(String component, byte type,
                      UUID sender, ProtocolVersion version) {
    Preconditions.checkNotNull(component, "component");
    GenericChatPacket packet;
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      packet = new SystemChatPacket();
    } else {
      packet = new LegacyChatPacket();
      ((LegacyChatPacket) packet).setSenderUuid(sender);
    }
    packet.setMessage(component);
    packet.setType(type);
    return packet;
  }

  /**
   * Create serverbound chat packet.
   *
   * @param version the protocol version
   * @param message the message
   * @return chat packet
   */
  public static GenericChatPacket createServerbound(ProtocolVersion version, String message) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      PlayerChatPacket packet = new PlayerChatPacket();
      packet.setMessage(message);
      packet.setType(CHAT_TYPE);
      packet.setTimeStamp(Instant.now());
      packet.setSaltSignature(EncryptionResponse.SaltSignature.EMPTY);
      return packet;
    } else {
      LegacyChatPacket packet = new LegacyChatPacket();
      packet.setMessage(message);
      packet.setType(CHAT_TYPE);
      packet.setSenderUuid(EMPTY_SENDER);
      return packet;
    }
  }
}
