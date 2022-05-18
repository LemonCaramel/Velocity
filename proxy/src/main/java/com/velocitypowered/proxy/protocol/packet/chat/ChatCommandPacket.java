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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ChatCommandPacket extends GenericChatPacket {

  private Instant timeStamp;
  private ArgumentSignatures argumentSignatures = ArgumentSignatures.EMPTY;
  private boolean signedPreview;

  public ChatCommandPacket() {
    this.commandPacket = true;
  }

  public Instant getTimeStamp() {
    return timeStamp;
  }

  public void setTimeStamp(Instant timeStamp) {
    this.timeStamp = timeStamp;
  }

  public ArgumentSignatures getArgumentSignatures() {
    return argumentSignatures;
  }

  public void setArgumentSignatures(ArgumentSignatures argumentSignatures) {
    this.argumentSignatures = argumentSignatures;
  }

  public boolean isSignedPreview() {
    return signedPreview;
  }

  public void setSignedPreview(boolean signedPreview) {
    this.signedPreview = signedPreview;
  }

  @Override
  public String toString() {
    return "ChatCommandPacket{"
        + "command=" + message
        + ", commandPacket=" + commandPacket
        + ", timeStamp=" + timeStamp
        + ", argumentSignatures=" + argumentSignatures
        + ", signedPreview=" + signedPreview
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.message = ProtocolUtils.readString(buf, MAX_SERVERBOUND_MESSAGE_LENGTH); // Command
    this.timeStamp = Instant.ofEpochMilli(buf.readLong());
    this.argumentSignatures = ArgumentSignatures.decode(buf);
    this.signedPreview = buf.readBoolean();
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    super.encode(buf, direction, version); // Command
    buf.writeLong(timeStamp.toEpochMilli());
    ArgumentSignatures.encode(buf, argumentSignatures);
    buf.writeBoolean(signedPreview);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  public static class ArgumentSignatures {
    public static final ArgumentSignatures EMPTY = new ArgumentSignatures(0L, Map.of());
    private final long salt;
    private final Map<String, byte[]> signatureMap;

    ArgumentSignatures(long salt, Map<String, byte[]> signatureMap) {
      this.salt = salt;
      this.signatureMap = signatureMap;
    }

    private static ArgumentSignatures decode(ByteBuf buf) {
      long salt = buf.readLong();

      int size = ProtocolUtils.readVarInt(buf);
      Map<String, byte[]> signatureMap = new HashMap<>(size);
      for (int index = 0; index < size; ++index) {
        signatureMap.put(ProtocolUtils.readString(buf, 16), ProtocolUtils.readByteArray(buf));
      }

      return new ArgumentSignatures(salt, signatureMap);
    }

    private static void encode(ByteBuf buf, ArgumentSignatures signatures) {
      buf.writeLong(signatures.salt);
      ProtocolUtils.writeVarInt(buf, signatures.signatureMap.size());
      signatures.signatureMap.forEach((key, value) -> {
        ProtocolUtils.writeString(buf, key);
        ProtocolUtils.writeByteArray(buf, value);
      });
    }

    @Override
    public String toString() {
      return "ArgumentSignatures{"
          + "salt=" + salt
          + ", signatureMap=" + signatureMap
          + '}';
    }
  }
}
