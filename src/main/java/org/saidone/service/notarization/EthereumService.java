/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
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

package org.saidone.service.notarization;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.config.EthereumConfig;
import org.saidone.exception.NotarizationException;
import org.saidone.service.NodeService;
import org.saidone.service.content.ContentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Service responsible for interacting with an Ethereum node to store document hashes.
 *
 * <p>
 *     This component wraps the minimal Web3j interactions required by the
 *     application. It creates the {@link Web3j} client on startup and uses the
 *     provided {@link Credentials} to sign transactions that embed document
 *     hashes. The bean is instantiated only when the application is configured
 *     to use the {@code ethereum} implementation of the notarization service.
 * </p>
 */
@Service
@Slf4j
@ConditionalOnExpression(
        "${application.service.vault.notarization.enabled}.equals(true) and '${application.service.vault.notarization.impl}'.equals('ethereum')"
)
public class EthereumService extends AbstractNotarizationService {

    /**
     * Configuration properties describing the Ethereum connection.
     */
    private final EthereumConfig config;

    /**
     * Client used to interact with the Ethereum node.
     */
    private Web3j web3j;

    /**
     * Credentials used to sign transactions sent by this service.
     */
    private Credentials credentials;

    /**
     * Null-recipient address used when broadcasting transactions that solely
     * contain notarization data. The Ethereum network ignores the recipient when
     * no value is transferred, therefore this placeholder is sufficient.
     */
    private static final String TO = "0x0000000000000000000000000000000000000000";

    /**
     * Creates the service with required dependencies.
     *
     * @param nodeService    service used to interact with nodes
     * @param contentService service computing document hashes
     * @param config         Ethereum configuration properties
     */
    public EthereumService(NodeService nodeService, ContentService contentService, EthereumConfig config) {
        super(nodeService, contentService);
        this.config = config;
    }

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
     * Fetches the hash data stored in the given Ethereum transaction.
     *
     * @param txHash the transaction identifier
     * @return the string stored in the transaction input data
     */
    @Override
    protected String getHash(String txHash) {
        try {
            val ethTransaction = web3j.ethGetTransactionByHash(txHash).send();
            val transactionOptional = ethTransaction.getTransaction();
            if (transactionOptional.isPresent()) {
                val tx = transactionOptional.get();
                val inputData = tx.getInput();
                byte[] inputBytes = Numeric.hexStringToByteArray(inputData);
                return new String(inputBytes, StandardCharsets.UTF_8);
            } else {
                throw new NotarizationException("Transaction not found");
            }
        } catch (IOException e) {
            log.trace(e.getMessage(), e);
            throw new NotarizationException(String.format("Error fetching transaction %s", txHash));
        }
    }

    /**
     * Sends a zero-value transaction containing the given hash as data.
     * <p>
     * The transaction is signed with the configured credentials and addressed
     * to the account defined in {@link EthereumConfig}.
     * </p>
     *
     * @param nodeId the node identifier
     * @param hash   the hash to store on the blockchain
     * @return the resulting Ethereum transaction hash
     */
    @Override
    protected String putHash(String nodeId, String hash) {
        try {
            val chainId = web3j.ethChainId().send().getChainId();
            val txManager = new RawTransactionManager(web3j, credentials, chainId.longValue());
            val data = Numeric.toHexString(hash.getBytes(StandardCharsets.UTF_8));

            // estimate gas needed
            val gasPriceResponse = web3j.ethGasPrice().send();
            val gasPrice = gasPriceResponse.getGasPrice();
            val estimateGas = web3j.ethEstimateGas(
                    Transaction.createFunctionCallTransaction(
                            config.getAccount(),
                            null,
                            gasPrice,
                            null,
                            TO,
                            BigInteger.ZERO,
                            data
                    )
            ).send();

            val tx = txManager.sendTransaction(
                    gasPrice,
                    estimateGas.getAmountUsed(),
                    config.getAccount(),
                    data,
                    BigInteger.ZERO);
            val txHash = tx.getTransactionHash();
            log.debug("Notarized node {} with tx id {}", nodeId, txHash);
            return txHash;
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            throw new NotarizationException(String.format("Error sending transaction for node %s: %s", nodeId, e.getMessage()));
        }
    }

}
