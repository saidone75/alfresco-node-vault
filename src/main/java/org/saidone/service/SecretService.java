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

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.config.EncryptionConfig;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class SecretService extends BaseComponent {

    private final VaultTemplate vaultTemplate;
    private final EncryptionConfig properties;

    public byte[] getSecret() {
        if (properties.getVaultSecretPath() != null && properties.getVaultSecretKey() != null) {
            val response = vaultTemplate.read(properties.getVaultSecretPath());
            if (response.getData() == null) {
                throw new IllegalStateException("Secret not found in Vault");
            }
            return (((Map<?, ?>) response.getData().get("data")).get(properties.getVaultSecretKey())).toString().getBytes(StandardCharsets.UTF_8);
        } else {
            return properties.getSecret().getBytes(StandardCharsets.UTF_8);
        }
    }

}
