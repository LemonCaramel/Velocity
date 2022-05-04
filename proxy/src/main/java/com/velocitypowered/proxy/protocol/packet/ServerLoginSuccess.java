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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServerLoginSuccess implements MinecraftPacket {

  private @Nullable UUID uuid;
  private @Nullable String username;
  private @Nullable List<GameProfile.Property> properties;

  public UUID getUuid() {
    if (uuid == null) {
      throw new IllegalStateException("No UUID specified!");
    }
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  public String getUsername() {
    if (username == null) {
      throw new IllegalStateException("No username specified!");
    }
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public @Nullable List<GameProfile.Property> getProperties() {
    return properties;
  }

  public void setProperties(List<GameProfile.Property> properties) {
    this.properties = properties;
  }

  @Override
  public String toString() {
    return "ServerLoginSuccess{"
        + "uuid=" + uuid
        + ", username='" + username + '\''
        + ", properties=" + properties
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) >= 0) {
      uuid = ProtocolUtils.readUuidIntArray(buf);
    } else if (version.compareTo(ProtocolVersion.MINECRAFT_1_7_6) >= 0) {
      uuid = UUID.fromString(ProtocolUtils.readString(buf, 36));
    } else {
      uuid = UuidUtils.fromUndashed(ProtocolUtils.readString(buf, 32));
    }
    username = ProtocolUtils.readString(buf, 16);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      final int size = ProtocolUtils.readVarInt(buf);
      this.properties = new ArrayList<>(size);
      for (int index = 0; index < size; ++index) {
        final String name = ProtocolUtils.readString(buf);
        final String value = ProtocolUtils.readString(buf);
        final String signature = buf.readBoolean() ? ProtocolUtils.readString(buf) : null;
        this.properties.add(new GameProfile.Property(name, value, signature));
      }
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (uuid == null) {
      throw new IllegalStateException("No UUID specified!");
    }
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) >= 0) {
      ProtocolUtils.writeUuidIntArray(buf, uuid);
    } else if (version.compareTo(ProtocolVersion.MINECRAFT_1_7_6) >= 0) {
      ProtocolUtils.writeString(buf, uuid.toString());
    } else {
      ProtocolUtils.writeString(buf, UuidUtils.toUndashed(uuid));
    }
    if (username == null) {
      throw new IllegalStateException("No username specified!");
    }
    ProtocolUtils.writeString(buf, username);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      if (this.properties == null) {
        ProtocolUtils.writeVarInt(buf, 0); // No property
      } else {
        final int size = this.properties.size();
        ProtocolUtils.writeVarInt(buf, size);
        for (GameProfile.Property property : this.properties) {
          ProtocolUtils.writeString(buf, property.getName());
          ProtocolUtils.writeString(buf, property.getValue());
          if (property.hasSignature()) {
            buf.writeBoolean(true);
            ProtocolUtils.writeString(buf, property.getSignature());
          } else {
            buf.writeBoolean(false);
          }
        }
      }
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
