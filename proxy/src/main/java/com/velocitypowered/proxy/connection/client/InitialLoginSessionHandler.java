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

package com.velocitypowered.proxy.connection.client;

import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_19;
import static com.velocitypowered.proxy.VelocityServer.GENERAL_GSON;
import static com.velocitypowered.proxy.connection.VelocityConstants.EMPTY_BYTE_ARRAY;
import static com.velocitypowered.proxy.util.EncryptionUtils.LINE_SEPARATOR;
import static com.velocitypowered.proxy.util.EncryptionUtils.RSA_PUBLIC_KEY_FOOTER;
import static com.velocitypowered.proxy.util.EncryptionUtils.RSA_PUBLIC_KEY_HEADER;
import static com.velocitypowered.proxy.util.EncryptionUtils.decryptRsa;
import static com.velocitypowered.proxy.util.EncryptionUtils.generateServerId;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.packet.EncryptionRequest;
import com.velocitypowered.proxy.protocol.packet.EncryptionResponse;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponse;
import com.velocitypowered.proxy.protocol.packet.ServerLogin;
import com.velocitypowered.proxy.util.EncryptionUtils;
import io.netty.buffer.ByteBuf;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public class InitialLoginSessionHandler implements MinecraftSessionHandler {

  private static final Logger logger = LogManager.getLogger(InitialLoginSessionHandler.class);
  private static final Encoder MIME_ENCODER = Base64.getMimeEncoder(76,
      LINE_SEPARATOR.getBytes(StandardCharsets.UTF_8));
  private static final String MOJANG_HASJOINED_URL =
      System.getProperty("mojang.sessionserver", "https://sessionserver.mojang.com/session/minecraft/hasJoined")
          .concat("?username=%s&serverId=%s");

  private final VelocityServer server;
  private final MinecraftConnection mcConnection;
  private final LoginInboundConnection inbound;
  private @MonotonicNonNull ServerLogin login;
  private byte[] verify = EMPTY_BYTE_ARRAY;
  private PublicKey publicKey = null;
  private LoginState currentState = LoginState.LOGIN_PACKET_EXPECTED;

  InitialLoginSessionHandler(VelocityServer server, MinecraftConnection mcConnection,
                             LoginInboundConnection inbound) {
    this.server = Preconditions.checkNotNull(server, "server");
    this.mcConnection = Preconditions.checkNotNull(mcConnection, "mcConnection");
    this.inbound = Preconditions.checkNotNull(inbound, "inbound");
  }

  @Override
  public boolean handle(ServerLogin packet) {
    assertState(LoginState.LOGIN_PACKET_EXPECTED);
    this.currentState = LoginState.LOGIN_PACKET_RECEIVED;
    this.login = packet;

    if (mcConnection.getProtocolVersion().compareTo(MINECRAFT_1_19) >= 0) {
      final ServerLogin.PublicKeyData publicKey = this.login.getPublicKey();
      if (publicKey == null) {
        inbound.disconnect(Component.translatable("multiplayer.disconnect.missing_public_key"));
        return true;
      }

      final Instant expiresAt = publicKey.expiresAt;
      final String keyString = RSA_PUBLIC_KEY_HEADER + LINE_SEPARATOR
            + MIME_ENCODER.encodeToString(publicKey.key.getEncoded())
            + LINE_SEPARATOR + RSA_PUBLIC_KEY_FOOTER + LINE_SEPARATOR;
      final String signature = Base64.getEncoder().encodeToString(publicKey.keySig);
      final GameProfile.Property property = new GameProfile.Property(
          "publicKey", expiresAt.toEpochMilli() + keyString, signature
      );

      if (property.getSignature().isEmpty()) {
        logger.error("Signature is missing from Property {}", property.getName());
        inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_public_key_signature"));
        return true;
      }

      if (!EncryptionUtils.isSignatureValid(property)) {
        logger.error("Property {} has been tampered with (signature invalid)", property.getName());
        inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_public_key_signature"));
        return true;
      }

      /* An unnecessary process - Don't trust the server? */
      // 1. SKIP TIMESTAMP CHECK
      // 2. SKIP PUBLIC KEY PARSE CHECK
      // 3. SKIP CREATE PAIR

      if (expiresAt.isBefore(Instant.now())) {
        inbound.disconnect(Component.text("Expired profile public key"));
        return true;
      }

      this.publicKey = publicKey.key;
    }

    PreLoginEvent event = new PreLoginEvent(inbound, login.getUsername());
    server.getEventManager().fire(event)
        .thenRunAsync(() -> {
          if (mcConnection.isClosed()) {
            // The player was disconnected
            return;
          }

          PreLoginComponentResult result = event.getResult();
          Optional<Component> disconnectReason = result.getReasonComponent();
          if (disconnectReason.isPresent()) {
            // The component is guaranteed to be provided if the connection was denied.
            inbound.disconnect(disconnectReason.get());
            return;
          }

          inbound.loginEventFired(() -> {
            if (mcConnection.isClosed()) {
              // The player was disconnected
              return;
            }

            mcConnection.eventLoop().execute(() -> {
              if (!result.isForceOfflineMode() && (server.getConfiguration().isOnlineMode()
                  || result.isOnlineModeAllowed())) {
                // Request encryption.
                EncryptionRequest request = generateEncryptionRequest();
                this.verify = Arrays.copyOf(request.getVerifyToken(), 4);
                mcConnection.write(request);
                this.currentState = LoginState.ENCRYPTION_REQUEST_SENT;
              } else {
                mcConnection.setSessionHandler(new AuthSessionHandler(
                    server, inbound, GameProfile.forOfflinePlayer(login.getUsername()), false
                ));
              }
            });
          });
        }, mcConnection.eventLoop())
        .exceptionally((ex) -> {
          logger.error("Exception in pre-login stage", ex);
          return null;
        });

    return true;
  }

  @Override
  public boolean handle(LoginPluginResponse packet) {
    this.inbound.handleLoginPluginResponse(packet);
    return true;
  }

  @Override
  public boolean handle(EncryptionResponse packet) {
    assertState(LoginState.ENCRYPTION_REQUEST_SENT);
    this.currentState = LoginState.ENCRYPTION_RESPONSE_RECEIVED;

    ServerLogin login = this.login;
    if (login == null) {
      throw new IllegalStateException("No ServerLogin packet received yet.");
    }

    if (verify.length == 0) {
      throw new IllegalStateException("No EncryptionRequest packet sent yet.");
    }

    try {
      KeyPair serverKeyPair = server.getServerKeyPair();
      boolean legacyProcess = true;

      // Minecraft 1.19+ Login process
      if (mcConnection.getProtocolVersion().compareTo(MINECRAFT_1_19) >= 0) {
        if (packet.getSaltSignature() != null) {
          if (this.publicKey == null) {
            inbound.disconnect(Component.translatable("multiplayer.disconnect.missing_public_key"));
            return true;
          }
          final Signature verifySig = Signature.getInstance("SHA256withRSA");
          verifySig.initVerify(this.publicKey);
          verifySig.update(this.verify);
          verifySig.update(packet.getSaltSignature().saltAsBytes());
          if (!verifySig.verify(packet.getSaltSignature().signature)) {
            inbound.disconnect(Component.text("Protocol error"));
            return true;
          }
          legacyProcess = false;
        }
      }

      // Minecraft <= 1.18 Login process
      if (legacyProcess) {
        byte[] decryptedVerifyToken = decryptRsa(serverKeyPair, packet.getVerifyToken());
        if (!MessageDigest.isEqual(verify, decryptedVerifyToken)) {
          throw new IllegalStateException("Unable to successfully decrypt the verification token.");
        }
      }

      byte[] decryptedSharedSecret = decryptRsa(serverKeyPair, packet.getSharedSecret());
      String serverId = generateServerId(decryptedSharedSecret, serverKeyPair.getPublic());

      String playerIp = ((InetSocketAddress) mcConnection.getRemoteAddress()).getHostString();
      String url = String.format(MOJANG_HASJOINED_URL,
          urlFormParameterEscaper().escape(login.getUsername()), serverId);

      if (server.getConfiguration().shouldPreventClientProxyConnections()) {
        url += "&ip=" + urlFormParameterEscaper().escape(playerIp);
      }

      ListenableFuture<Response> hasJoinedResponse = server.getAsyncHttpClient().prepareGet(url)
          .execute();
      hasJoinedResponse.addListener(() -> {
        if (mcConnection.isClosed()) {
          // The player disconnected after we authenticated them.
          return;
        }

        // Go ahead and enable encryption. Once the client sends EncryptionResponse, encryption
        // is enabled.
        try {
          mcConnection.enableEncryption(decryptedSharedSecret);
        } catch (GeneralSecurityException e) {
          logger.error("Unable to enable encryption for connection", e);
          // At this point, the connection is encrypted, but something's wrong on our side and
          // we can't do anything about it.
          mcConnection.close(true);
          return;
        }

        try {
          Response profileResponse = hasJoinedResponse.get();
          if (profileResponse.getStatusCode() == 200) {
            // All went well, initialize the session.
            mcConnection.setSessionHandler(new AuthSessionHandler(
                server, inbound, GENERAL_GSON.fromJson(profileResponse.getResponseBody(), GameProfile.class), true
            ));
          } else if (profileResponse.getStatusCode() == 204) {
            // Apparently an offline-mode user logged onto this online-mode proxy.
            inbound.disconnect(Component.translatable("velocity.error.online-mode-only",
                NamedTextColor.RED));
          } else {
            // Something else went wrong
            logger.error(
                "Got an unexpected error code {} whilst contacting Mojang to log in {} ({})",
                profileResponse.getStatusCode(), login.getUsername(), playerIp);
            inbound.disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
          }
        } catch (ExecutionException e) {
          logger.error("Unable to authenticate with Mojang", e);
          inbound.disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
        } catch (InterruptedException e) {
          // not much we can do usefully
          Thread.currentThread().interrupt();
        }
      }, mcConnection.eventLoop());
    } catch (GeneralSecurityException e) {
      logger.error("Unable to enable encryption", e);
      mcConnection.close(true);
    }
    return true;
  }

  private EncryptionRequest generateEncryptionRequest() {
    byte[] verify = new byte[4];
    ThreadLocalRandom.current().nextBytes(verify);

    EncryptionRequest request = new EncryptionRequest();
    request.setPublicKey(server.getServerKeyPair().getPublic().getEncoded());
    request.setVerifyToken(verify);
    return request;
  }

  @Override
  public void handleUnknown(ByteBuf buf) {
    mcConnection.close(true);
  }

  @Override
  public void disconnected() {
    this.inbound.cleanup();
  }

  private void assertState(LoginState expectedState) {
    if (this.currentState != expectedState) {
      if (MinecraftDecoder.DEBUG) {
        logger.error("{} Received an unexpected packet requiring state {}, but we are in {}", inbound,
            expectedState, this.currentState);
      }
      mcConnection.close(true);
    }
  }

  private enum LoginState {
    LOGIN_PACKET_EXPECTED,
    LOGIN_PACKET_RECEIVED,
    ENCRYPTION_REQUEST_SENT,
    ENCRYPTION_RESPONSE_RECEIVED
  }
}
