/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.service.crypto;

import java.io.InputStream;

/**
 * Abstraction for stream based symmetric encryption and decryption services.
 * Implementations return {@link InputStream} instances that must be consumed
 * and closed by the caller. Concrete implementations include
 * {@link JcaCryptoServiceImpl} and {@link BcCryptoServiceImpl}.
 */
public interface CryptoService {

    /**
     * Encrypts the provided data stream.
     *
     * @param inputStream plaintext data to encrypt
     * @param secret      secret material used to derive the encryption key
     * @return a stream containing the encrypted data
     */
    InputStream encrypt(InputStream inputStream, Secret secret);

    /**
     * Decrypts an encrypted data stream.
     *
     * @param inputStream the encrypted data
     * @return a stream yielding the decrypted plaintext
     */
    InputStream decrypt(InputStream inputStream);

    /**
     * Encrypts a text value and returns the Base64 encoded result.
     *
     * @param text   the text to encrypt
     * @param secret secret material used to derive the encryption key
     * @return encrypted text encoded in Base64
     */
    String encryptText(String text, Secret secret);

    /**
     * Decrypts a Base64 encoded encrypted text value.
     *
     * @param encryptedText the Base64 encoded encrypted text
     * @return the decrypted plain text
     */
    String decryptText(String encryptedText);

}
