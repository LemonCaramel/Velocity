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

package com.velocitypowered.proxy.util;

import com.velocitypowered.api.util.GameProfile;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

public enum EncryptionUtils {
  ;
  public static final String RSA_PUBLIC_KEY_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
  public static final String RSA_PUBLIC_KEY_FOOTER = "-----END RSA PUBLIC KEY-----";
  public static final String LINE_SEPARATOR = "\n";
  private static final PublicKey SIGNATURE_KEY;

  static {
    try {
      byte[] keyBytes = EncryptionUtils.class.getResourceAsStream("/yggdrasil_session_pubkey.der").readAllBytes();
      SIGNATURE_KEY = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    } catch (Exception e) {
      throw new ExceptionInInitializerError("Can't read the yggdrasil public key.");
    }
  }

  /**
   * Generates an RSA key pair.
   *
   * @param keysize the key size (in bits) for the RSA key pair
   * @return the generated key pair
   */
  public static KeyPair createRsaKeyPair(final int keysize) {
    try {
      final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(keysize);
      return generator.generateKeyPair();
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException("Unable to generate RSA keypair", e);
    }
  }

  /**
   * Generates a hex digest in two's complement form for use with the Mojang joinedServer endpoint.
   *
   * @param digest the bytes to digest
   * @return the hex digest
   */
  public static String twosComplementHexdigest(byte[] digest) {
    return new BigInteger(digest).toString(16);
  }

  /**
   * Decrypts an RSA message.
   *
   * @param keyPair the key pair to use
   * @param bytes the bytes of the encrypted message
   * @return the decrypted message
   * @throws GeneralSecurityException if the message couldn't be decoded
   */
  public static byte[] decryptRsa(KeyPair keyPair, byte[] bytes) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("RSA");
    cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
    return cipher.doFinal(bytes);
  }

  /**
   * Generates the server ID for the hasJoined endpoint.
   *
   * @param sharedSecret the shared secret between the client and the proxy
   * @param key the RSA public key
   * @return the server ID
   */
  public static String generateServerId(byte[] sharedSecret, PublicKey key) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update(sharedSecret);
      digest.update(key.getEncoded());
      return twosComplementHexdigest(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Check the signature is valid.
   *
   * @param property the Property
   * @return if {@code true} signature is valid
   */
  public static boolean isSignatureValid(GameProfile.Property property) {
    try {
      final Signature signature = Signature.getInstance("SHA1withRSA");
      signature.initVerify(SIGNATURE_KEY);
      signature.update(property.getValue().getBytes());
      return signature.verify(Base64.getDecoder().decode(property.getSignature()));
    } catch (final NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
      e.printStackTrace();
      throw new AssertionError(e);
    }
  }

  /**
   * Generates RSA public keys using an encoded byte array.
   *
   * @param encodedKey encoded byte array key
   * @return the rsa public key
   */
  public static PublicKey generateRsaPublicKey(byte[] encodedKey) {
    try {
      return KeyFactory.getInstance("RSA").generatePublic(
          new X509EncodedKeySpec(encodedKey)
      );
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
