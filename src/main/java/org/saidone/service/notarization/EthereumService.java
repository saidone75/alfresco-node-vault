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

package org.saidone.service.notarization;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.config.EthereumConfig;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Service responsible for interacting with an Ethereum node to store document hashes.
 *
 * <p>This component wraps the minimal Web3j interactions required by the
 * application. It creates the {@link Web3j} client on startup and uses the
 * provided {@link Credentials} to sign transactions that embed document
 * hashes.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EthereumService extends BaseComponent {

    private final EthereumConfig config;

    private Web3j web3j;
    private Credentials credentials;

    /**
     * Initializes the Web3j client and credentials after dependency injection.
     * <p>
     * The client is built using the RPC URL from {@link EthereumConfig}. If a
     * private key is configured, corresponding {@link Credentials} are loaded to
     * allow signed transactions.
     * </p>
     */
    @PostConstruct
    @Override
    public void init() {
        super.init();
        web3j = Web3j.build(new HttpService(config.getRpcUrl()));
        if (config.getPrivateKey() != null && !config.getPrivateKey().isBlank()) {
            credentials = Credentials.create(config.getPrivateKey());
        }
    }

    /**
     * Shuts down the Web3j client before the bean is destroyed.
     */
    @PreDestroy
    @Override
    public void stop() {
        if (web3j != null) {
            web3j.shutdown();
        }
        super.stop();
    }

    /**
     * Sends a zero-value transaction containing the given hash as data.
     * <p>
     * The transaction is signed with the configured credentials and addressed
     * to the account defined in {@link EthereumConfig}.
     * </p>
     *
     * @param nodeId the Alfresco node identifier used for logging
     * @param hash   the hash to store on the blockchain
     * @return the resulting Ethereum transaction hash
     */
    public String storeHash(String nodeId, String hash) {
        try {
            val chainId = web3j.ethChainId().send().getChainId();
            val txManager = new RawTransactionManager(web3j, credentials, chainId.longValue());
            val data = Numeric.toHexString(hash.getBytes(StandardCharsets.UTF_8));
            val tx = txManager.sendTransaction(
                    // FIXME get gas price
                    BigInteger.ZERO,
                    // FIXME estimate gas limit
                    BigInteger.valueOf(50000L),
                    config.getAccount(),
                    data,
                    BigInteger.ZERO);
            val txHash = tx.getTransactionHash();
            log.debug("Notarized node {} with tx {}", nodeId, txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Error sending transaction for node {}: {}", nodeId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
