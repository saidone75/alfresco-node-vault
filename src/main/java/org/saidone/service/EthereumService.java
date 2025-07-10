package org.saidone.service;

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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EthereumService extends BaseComponent {

    private final EthereumConfig config;

    private Web3j web3j;
    private Credentials credentials;

    @PostConstruct
    @Override
    public void init() {
        super.init();
        web3j = Web3j.build(new HttpService(config.getRpcUrl()));
        if (config.getPrivateKey() != null && !config.getPrivateKey().isBlank()) {
            credentials = Credentials.create(config.getPrivateKey());
        }
    }

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
     *
     * @param nodeId the node identifier
     * @param hash   the hash to store on the blockchain
     * @return the transaction hash
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
