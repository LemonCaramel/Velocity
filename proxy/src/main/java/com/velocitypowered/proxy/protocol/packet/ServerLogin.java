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
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServerLogin implements MinecraftPacket {

  private static final QuietDecoderException EMPTY_USERNAME = new QuietDecoderException("Empty username!");

  private @Nullable String username;
  private @Nullable CompoundBinaryTag publicKey;

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

  public CompoundBinaryTag getPublicKey() {
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
        publicKey = ProtocolUtils.readCompoundTag(buf, BinaryTagIO.reader());
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
        System.out.println("ServerLogin encode 4");
        ProtocolUtils.writeCompoundTag(buf, publicKey);
      }
    }
  }

  @Override
  public int expectedMaxLength(ByteBuf buf, Direction direction, ProtocolVersion version) {
    // Accommodate the rare (but likely malicious) use of UTF-8 usernames, since it is technically
    // legal on the protocol level.
    final int origin = 1 + (16 * 4);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      // https://api.minecraftservices.com/player/certificates
      // Authorization: Bearer <ygg_token>
      // expires_at(10 + 27 byte), [publicKey]signature(9 + 1024(max?) byte) , [public]key(3 + ???)
      return origin + 2048; // i don't have a good idea
    } else {
      return origin;
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
