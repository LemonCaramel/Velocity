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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LegacyChatPacket extends GenericChatPacket {

  public static final int MAX_SERVERBOUND_MESSAGE_LENGTH = 256;
  public static final UUID EMPTY_SENDER = new UUID(0, 0);

  private @Nullable UUID sender;

  public LegacyChatPacket() {
  }

  public UUID getSenderUuid() {
    return sender;
  }

  public void setSenderUuid(UUID sender) {
    this.sender = sender;
  }

  @Override
  public String toString() {
    return "LegacyChatPacket{"
        + "type=" + type
        + ", message='" + message + '\''
        + ", sender=" + sender
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    super.decode(buf, direction, version);
    if (direction == ProtocolUtils.Direction.CLIENTBOUND && version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      type = buf.readByte();
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) >= 0) {
        sender = ProtocolUtils.readUuid(buf);
      }
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (message == null) {
      throw new IllegalStateException("Message is not specified");
    }
    ProtocolUtils.writeString(buf, message);
    if (direction == ProtocolUtils.Direction.CLIENTBOUND && version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      buf.writeByte(type);
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) >= 0) {
        ProtocolUtils.writeUuid(buf, sender == null ? EMPTY_SENDER : sender);
      }
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
