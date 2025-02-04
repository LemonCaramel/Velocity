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
import com.velocitypowered.proxy.protocol.packet.EncryptionResponse;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

public class PlayerChatPacket extends GenericChatPacket {

  protected @Nullable String unsignedMessage;
  private @Nullable ChatSender sender;
  private Instant timeStamp;
  private EncryptionResponse.SaltSignature saltSignature;
  private boolean previewSigned;

  public String getUnsignedMessage() {
    return unsignedMessage;
  }

  public void setUnsignedMessage(String unsignedMessage) {
    this.unsignedMessage = unsignedMessage;
  }

  public ChatSender getSender() {
    return sender;
  }

  public void setSender(ChatSender sender) {
    this.sender = sender;
  }

  public Instant getTimeStamp() {
    return timeStamp;
  }

  public void setTimeStamp(Instant timeStamp) {
    this.timeStamp = timeStamp;
  }

  public EncryptionResponse.SaltSignature getSaltSignature() {
    return saltSignature;
  }

  public void setSaltSignature(EncryptionResponse.SaltSignature saltSignature) {
    this.saltSignature = saltSignature;
  }

  public boolean isPreviewSigned() {
    return previewSigned;
  }

  public void setPreviewSigned(boolean previewSigned) {
    this.previewSigned = previewSigned;
  }

  @Override
  public String toString() {
    return "PlayerChatPacket{"
        + "type=" + type
        + ", message='" + message + '\''
        + ", commandPacket=" + commandPacket
        + ", unsignedMessage='" + unsignedMessage + '\''
        + ", sender=" + sender
        + ", timeStamp=" + timeStamp
        + ", saltSignature=" + saltSignature
        + ", previewSigned=" + previewSigned
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    super.decode(buf, direction, version);
    if (direction == ProtocolUtils.Direction.CLIENTBOUND) {
      if (buf.readBoolean()) {
        this.unsignedMessage = ProtocolUtils.readString(buf);
      }
      this.type = ProtocolUtils.readVarInt(buf);
      this.sender = ChatSender.decode(buf);
    }
    this.timeStamp = Instant.ofEpochMilli(buf.readLong());
    this.saltSignature = EncryptionResponse.SaltSignature.decode(buf);
    if (direction == ProtocolUtils.Direction.SERVERBOUND) {
      this.previewSigned = buf.readBoolean();
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    super.encode(buf, direction, version);
    if (direction == ProtocolUtils.Direction.CLIENTBOUND) {
      if (unsignedMessage == null) {
        buf.writeBoolean(false);
      } else {
        buf.writeBoolean(true);
        ProtocolUtils.writeString(buf, unsignedMessage);
      }
      ProtocolUtils.writeVarInt(buf, type);
      if (sender == null) {
        throw new IllegalStateException("Sender is not specified");
      }
      ChatSender.encode(buf, sender);
    }
    buf.writeLong(timeStamp.toEpochMilli());
    EncryptionResponse.SaltSignature.encode(buf, saltSignature);
    if (direction == ProtocolUtils.Direction.SERVERBOUND) {
      buf.writeBoolean(previewSigned);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  public static class ChatSender {
    public final UUID uuid;
    public final String name;
    public final @Nullable String teamName;

    ChatSender(UUID uuid, String component, @Nullable String teamName) {
      this.uuid = uuid;
      this.name = component;
      this.teamName = teamName;
    }

    private static void encode(ByteBuf buf, ChatSender sender) {
      ProtocolUtils.writeUuid(buf, sender.uuid);
      ProtocolUtils.writeString(buf, sender.name);
      boolean hasTeamName = (sender.teamName != null);
      buf.writeBoolean(hasTeamName);
      if (hasTeamName) {
        ProtocolUtils.writeString(buf, sender.teamName);
      }
    }

    private static ChatSender decode(ByteBuf buf) {
      UUID uuid = ProtocolUtils.readUuid(buf);
      String name = ProtocolUtils.readString(buf);
      String teamName = buf.readBoolean() ? ProtocolUtils.readString(buf) : null;
      return new ChatSender(uuid, name, teamName);
    }

    @Override
    public String toString() {
      return "ChatSender{"
          + "uuid=" + uuid
          + ", name='" + name + '\''
          + ", teamName='" + teamName + '\''
          + '}';
    }
  }
}
