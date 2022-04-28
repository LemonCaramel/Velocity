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

import static com.velocitypowered.proxy.connection.VelocityConstants.EMPTY_BYTE_ARRAY;

import com.google.common.primitives.Longs;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;

public class EncryptionResponse implements MinecraftPacket {

  private byte[] sharedSecret = EMPTY_BYTE_ARRAY;
  private byte[] verifyToken = EMPTY_BYTE_ARRAY;
  private SaltSignature saltSignature = null;

  public byte[] getSharedSecret() {
    return sharedSecret.clone();
  }

  public byte[] getVerifyToken() {
    return verifyToken.clone();
  }

  public SaltSignature getSaltSignature() {
    return saltSignature;
  }

  @Override
  public String toString() {
    return "EncryptionResponse{"
        + "sharedSecret=" + Arrays.toString(sharedSecret)
        + ", verifyToken=" + Arrays.toString(verifyToken)
        + ", saltSignature=" + saltSignature
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      this.sharedSecret = ProtocolUtils.readByteArray(buf, 128);
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
        if (buf.readBoolean()) {
          this.verifyToken = ProtocolUtils.readByteArray(buf, 128);
        } else {
            this.saltSignature = SaltSignature.decode(buf);
        }
      } else {
        this.verifyToken = ProtocolUtils.readByteArray(buf, 128);
      }
    } else {
      this.sharedSecret = ProtocolUtils.readByteArray17(buf);
      this.verifyToken = ProtocolUtils.readByteArray17(buf);
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      ProtocolUtils.writeByteArray(buf, sharedSecret);
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
        if (saltSignature == null) {
          ProtocolUtils.writeByteArray(buf, verifyToken);
        } else {
          SaltSignature.encode(buf, saltSignature);
        }
      } else {
        ProtocolUtils.writeByteArray(buf, verifyToken);
      }
    } else {
      ProtocolUtils.writeByteArray17(sharedSecret, buf, false);
      ProtocolUtils.writeByteArray17(verifyToken, buf, false);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public int expectedMaxLength(ByteBuf buf, Direction direction, ProtocolVersion version) {
    // It turns out these come out to the same length, whether we're talking >=1.8 or not.
    // The length prefix always winds up being 2 bytes.
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_19) >= 0) {
      return 397;
    } else {
      return 260;
    }
  }

  @Override
  public int expectedMinLength(ByteBuf buf, Direction direction, ProtocolVersion version) {
    return expectedMaxLength(buf, direction, version);
  }

  public static class SaltSignature {

    public final long salt;
    public final byte[] signature;
    public static final SaltSignature EMPTY = new SaltSignature(0L, new byte[]{});

    public SaltSignature(long salt, byte[] signature) {
      this.salt = salt;
      this.signature = signature;
    }

    public static SaltSignature decode(ByteBuf buf) {
      return new SaltSignature(buf.readLong(), ProtocolUtils.readByteArray(buf));
    }

    public static void encode(ByteBuf buf, SaltSignature signature) {
      buf.writeLong(signature.salt);
      ProtocolUtils.writeByteArray(buf, signature.signature);
    }

    public boolean isValid() {
      return this.signature.length > 0;
    }

    public byte[] saltAsBytes() {
      return Longs.toByteArray(this.salt);
    }

    @Override
    public String toString() {
      return "SaltSignature{"
          + "salt=" + salt
          + ", signature=" + Arrays.toString(signature)
          + '}';
    }
  }
}
