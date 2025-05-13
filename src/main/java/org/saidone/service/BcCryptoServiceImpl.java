/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.saidone.service;

import jakarta.validation.constraints.Min;
import lombok.Setter;
import lombok.val;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.saidone.config.EncryptionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.SecureRandom;
import java.security.Security;

@Service
@Setter
@ConfigurationProperties(prefix = "application.service.vault.encryption.bc")
@ConditionalOnExpression("${application.service.vault.encryption.enabled:true} == true && '${application.service.vault.encryption.impl:}' == 'bc'")
public class BcCryptoServiceImpl extends AbstractCryptoService implements CryptoService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Min(16)
    private int saltLength;
    @Min(12)
    private int nonceLength;
    private static final String CIPHER_TRANSFORMATION = "ChaCha20-Poly1305";
    private static final String KEY_ALGORITHM = "ChaCha20";

    private static final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public void configure(EncryptionProperties properties) {
        this.secret = properties.getSecret();
        this.kdf = properties.getKdf();
        this.saltLength = properties.getBc().getSaltLength();
        this.nonceLength = properties.getBc().getNonceLength();
    }

    @Override
    public InputStream encrypt(InputStream inputStream) {
        try {
            // Generate random salt and nonce
            byte[] salt = new byte[saltLength];
            secureRandom.nextBytes(salt);

            byte[] nonce = new byte[nonceLength];
            secureRandom.nextBytes(nonce);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(KEY_ALGORITHM, salt);

            // Initialize ChaCha20-Poly1305 cipher
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            val spec = new IvParameterSpec(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Concatenate salt and nonce
            byte[] saltAndNonce = new byte[saltLength + nonceLength];
            System.arraycopy(salt, 0, saltAndNonce, 0, saltLength);
            System.arraycopy(nonce, 0, saltAndNonce, saltLength, nonceLength);

            // Prepend salt and nonce to encrypted stream  
            return new SequenceInputStream(
                    new ByteArrayInputStream(saltAndNonce),
                    new CipherInputStream(inputStream, cipher)
            );
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption", e);
        }
    }

    @Override
    public InputStream decrypt(InputStream inputStream) {
        try {
            // Read salt from stream
            byte[] salt = inputStream.readNBytes(saltLength);

            // Read nonce from stream
            byte[] nonce = inputStream.readNBytes(nonceLength);

            // Derive key using configured KDF
            val secretKey = deriveSecretKey(KEY_ALGORITHM, salt);

            // Initialize ChaCha20-Poly1305 cipher for decryption
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            val spec = new IvParameterSpec(nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Error during decryption", e);
        }
    }

}
