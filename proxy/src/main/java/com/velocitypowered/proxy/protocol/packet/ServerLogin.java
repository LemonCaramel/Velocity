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

package com.velocitypowered.proxy.protocol.packet;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import com.velocitypowered.proxy.util.EncryptionUtils;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;

public class ServerLogin implements MinecraftPacket {

  private static final QuietDecoderException EMPTY_USERNAME = new QuietDecoderException("Empty username!");

  private @Nullable String username;
  private @Nullable PublicKeyData publicKey;

  public ServerLogin() {
  }

  public ServerLogin(String username) {
    this.username = Preconditions.checkNotNull(username, "username");
  }

  public String getUsername() {
    if (username == null) {
      throw new IllegalStateException("No username found!");
    }
    return username;
  }

  public PublicKeyData getPublicKey() {
    return publicKey;
  }

  @Override
  public String toString() {
    return "ServerLogin{"
        + "username='" + username + '\''
        + ", publicKey='" + publicKey + '\''
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    username = ProtocolUtils.readString(buf, 16);
    if (username.isEmpty()) {
      throw EMPTY_USERNAME;
    }
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      if (buf.readBoolean()) { // Optional
        publicKey = PublicKeyData.decode(buf);
      }
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (username == null) {
      throw new IllegalStateException("No username found!");
    }
    ProtocolUtils.writeString(buf, username);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      boolean hasPublicKey = (publicKey != null);
      buf.writeBoolean(hasPublicKey);
      if (hasPublicKey) { // Optional
        PublicKeyData.encode(buf, publicKey);
      }
    }
  }

  @Override
  public int expectedMaxLength(ByteBuf buf, Direction direction, ProtocolVersion version) {
    // Accommodate the rare (but likely malicious) use of UTF-8 usernames, since it is technically
    // legal on the protocol level.
    final int origin = 1 + (16 * 4);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      return origin + 8 + (512 + 2) + (4096 + 2);
    } else {
      return origin;
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  public static class PublicKeyData {
    public final Instant expiresAt;
    public final PublicKey key;
    public final byte[] keySig;

    PublicKeyData(Instant expiresAt, PublicKey key, byte[] keySig) {
      this.expiresAt = expiresAt;
      this.key = key;
      this.keySig = keySig;
    }

    public static void encode(ByteBuf buf, PublicKeyData data) {
      buf.writeLong(data.expiresAt.toEpochMilli());
      ProtocolUtils.writeByteArray(buf, data.key.getEncoded());
      ProtocolUtils.writeByteArray(buf, data.keySig);
    }

    public static PublicKeyData decode(ByteBuf buf) {
      Instant expiresAt = Instant.ofEpochMilli(buf.readLong());
      byte[] encodedKey = ProtocolUtils.readByteArray(buf, 512);
      PublicKey key = EncryptionUtils.generateRsaPublicKey(encodedKey);
      byte[] keySig = ProtocolUtils.readByteArray(buf, 4096);
      return new PublicKeyData(expiresAt, key, keySig);
    }

    @Override
    public String toString() {
      return "PublicKeyData{"
          + "expiresAt=" + expiresAt
          + ", key='" + key + '\''
          + ", keySig=" + Arrays.toString(keySig)
          + '}';
    }
  }
}
