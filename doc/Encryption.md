# Encryption Options Documentation

## Overview

This document outlines the encryption capabilities available in the Alfresco Node Vault application. The system supports two authenticated encryption algorithms:

1. **AES-256-GCM** - selected by `impl: jca`
2. **ChaCha20-Poly1305** - selected by `impl: bc`

## Configuration

The encryption service can be configured in your `application.yml` file:

```yaml
application:
  service:
    vault:
      encryption:
        content:
          # Enable/disable binary content encryption (S3, GridFS, Ozone)
          enabled: ${VAULT_ENCRYPT_CONTENT:false}
        metadata:
          # Enable/disable node metadata encryption on MongoDB
          enabled: ${VAULT_ENCRYPT_METADATA:false}
        # Versioned encryption secret in HashiCorp Vault KV v2
        vault-secret-kv-mount: ${VAULT_ENCRYPTION_KV_MOUNT:secret}
        vault-secret-path: ${VAULT_ENCRYPTION_SECRET_PATH:AlfrescoNodeVault}
        vault-secret-key: ${VAULT_ENCRYPTION_SECRET_KEY:anv.secret}
        # Encryption implementation: 'jca' or 'bc'
        impl: bc
        jca:
          # Salt length in bytes for key derivation
          salt-length: 16
          # Initialization vector length in bytes for AES-GCM
          iv-length: 12
        bc:
          # Salt length in bytes for key derivation
          salt-length: 16
          # Nonce length in bytes for ChaCha20-Poly1305
          nonce-length: 12
        kdf:
          # Key derivation function: pbkdf2, hkdf, or argon2
          impl: pbkdf2
          pbkdf2:
            # Number of PBKDF2 iterations
            iterations: 100000
          hkdf:
            # Optional context info for HKDF
            info: hkdf-info
          argon2:
            # Number of parallel threads
            parallelism: 1
            # Memory usage in KB 
            memory: 65536
            # Number of iterations
            iterations: 3
```

The encryption secret is read from HashiCorp Vault KV v2 at the configured mount/path/key.
Set `VAULT_URL` for the server address
and configure authentication under `spring.cloud.vault` (the development configuration uses token `root`).
The application identity needs access to the secret and its historical versions.

## Key Derivation Functions (KDFs)

The system supports multiple key derivation functions for deriving encryption keys from the master secret:

### PBKDF2 (Default)

**Configuration**: `kdf.impl: "pbkdf2"`

PBKDF2 (Password-Based Key Derivation Function 2) is a widely supported KDF that applies a pseudorandom function to derive keys.

- **Pros**: Widely supported, simple parameters
- **Cons**: More vulnerable to hardware acceleration attacks than newer alternatives
- **Recommended Settings**:
    - iterations: 100,000 minimum (higher values increase security but reduce performance)

### Argon2

**Configuration**: `kdf.impl: "argon2"`

Argon2 is a modern KDF designed to be resistant to GPU, ASIC, and side-channel attacks. It won the Password Hashing Competition in 2015.

- **Pros**: Strong resistance to parallel attacks, memory-hardness deters hardware acceleration
- **Cons**: Less widely supported than PBKDF2, high memory usage
- **Recommended Settings**:
    - memory: 65536 (64MB) or higher
    - parallelism: 4 (adjust based on available CPU cores)
    - iterations: 3-4

### HKDF

**Configuration**: `kdf.impl: "hkdf"`

HKDF (HMAC-based Key Derivation Function) is designed for deriving keys from already-strong input keying material.

- **Pros**: Very efficient for high-quality input keys
- **Cons**: Not designed for password-based key derivation
- **Use Case**: Best when your master key is already highly random, not for password-based scenarios

## Encryption Algorithms

Both algorithms use JCA APIs with an explicitly selected Bouncy Castle provider.

| Configuration | Algorithm | Key / tag | Salt / nonce |
|---------------|-----------|-----------|--------------|
| `impl: jca` | AES-256-GCM | 256 / 128 bits | 16..64 / 12 bytes |
| `impl: bc` | ChaCha20-Poly1305 | 256 / 128 bits | 16..64 / 12 bytes |

The default salt length is 16 bytes. Existing ANV v1 payloads retain their own algorithm and KDF parameters.

### Notes about FIPS compliance

FIPS 140 compliance depends on the deployment and is the responsibility of the integrator.
A compliant installation requires integrating a validated module such as
[Bouncy Castle FIPS](https://www.bouncycastle.org/download/), adapting the current code and dependencies,
and using approved algorithms and parameters within the module's validated environment and Security Policy,
including key derivation and random generation. Selecting `impl: jca` alone does not provide compliance; see the
[NIST CMVP requirements](https://csrc.nist.gov/projects/cryptographic-module-validation-program/validated-modules).

## Security Considerations

1. **Storage of Master Secret**:
    - Store the encryption secret in HashiCorp Vault KV v2 and retain versions needed to decrypt existing data
    - Configure Vault authentication separately from the encryption secret
    - `SecretService` does not cache encryption secrets, and KDFs zero their source byte buffers on the normal execution path to reduce their time in memory.
    - Never commit secrets to source control

2. **Nonce/IV and Key Version Usage**:
    - Each encryption operation uses a new random nonce/IV
    - The system authenticates the complete ANV header, including cipher, KDF parameters, key version, salt and nonce

3. **Processing Model**:
    - The system uses streaming encryption/decryption to handle large files efficiently
    - Minimal memory footprint even with large files

## Performance Considerations

- **ChaCha20-Poly1305 (BC)** typically performs better on:
    - ARM and RISC-V CPUs
    - Older hardware without AES-NI

- **AES-GCM (JCA)** typically performs better on:
    - Modern x86 servers with AES-NI
    - Cloud environments with hardware-accelerated AES

## Format of Encrypted Data

All newly encrypted binary content and Base64-encoded metadata use ANV v1:

```text
[magic:4][version:1][headerLength:2][headerBody][ciphertext][tag:16]
```

Integers are big-endian. `magic` is `89 41 4e 56`; version is `1`. Header length
includes the seven-byte prefix and must not exceed 4096 bytes. The header body is:

| Field | Encoding |
|-------|----------|
| Cipher ID | 1 byte: 1 = AES-256-GCM, 2 = ChaCha20-Poly1305 |
| Logical key reference | 1 byte: 1 = configured Vault secret |
| Vault secret version | 4 bytes, positive integer |
| KDF ID | 1 byte: 1 = PBKDF2-HMAC-SHA256, 2 = HKDF-SHA256, 3 = Argon2id |
| KDF parameters | Algorithm-specific encoding below |
| Salt | 2-byte length followed by 16..64 bytes |
| Nonce | 2-byte length followed by exactly 12 bytes |

KDF parameter encodings:

- PBKDF2: 4-byte iteration count, between 100,000 and 10,000,000.
- HKDF: 2-byte byte-length followed by UTF-8 `info`, at most 1024 bytes.
- Argon2id: version byte `0x13`, then 4-byte memory in KiB (65,536..262,144),
  iterations (3..10) and parallelism (1..16).

Keys are always 256 bits; tags are always 128 bits. Vault secrets are UTF-8 bytes.
PBKDF2 decodes them as UTF-8 characters for `PBEKeySpec`; HKDF and Argon2id use the
bytes directly. Cipher IDs identify algorithms, independent of Java provider;
both are processed incrementally using Bouncy Castle.

The exact bytes of the entire header are supplied as AEAD additional authenticated
data (AAD). Header and ciphertext alterations fail authentication. Parsing rejects
unknown versions/IDs, trailing header fields, invalid lengths and excessive KDF
costs before retrieving a secret. Header fields remain untrusted until the final
tag is verified. Streaming decryption can yield plaintext before that point:
consumers must read to EOF and handle errors; closing early does not authenticate
the document. Consumers requiring verified plaintext before use must stage it and
publish only after successful completion.

Secret locations and credentials are not embedded in payloads. Logical reference
1 resolves to the configured Vault mount/path/key, which must remain available
along with the recorded secret versions.

There is **no legacy reader**. Existing unversioned encrypted payloads are rejected.
Export them using the previous application version before upgrading and reimport,
or recreate disposable data. Unencrypted data is unaffected.

Implementation: [AnvCryptoService.java](../src/main/java/org/saidone/service/crypto/AnvCryptoService.java)
and [EncryptedPayloadHeader.java](../src/main/java/org/saidone/service/crypto/EncryptedPayloadHeader.java).

## Key Rotation and Retention

Use persistent Vault KV v2 storage. Review rotation needs annually and rotate on suspected exposure
or organizational requirements; ANV does not schedule rotations.

1. Before rotating, verify recoverable backups and inspect retention:
   ```bash
   vault read secret/config
   vault kv metadata get -mount=secret AlfrescoNodeVault
   ```
   Adjust mount/path as configured. Ensure `max_versions` can retain all required history plus the new
   version: exceeding it permanently destroys older versions. **Zero does not mean unlimited**;
   the key inherits the mount setting, whose default is 10. Disable `delete_version_after` at both
   levels and check existing scheduled deletions. See the
   [Vault KV v2 API](https://developer.hashicorp.com/vault/api-docs/secret/kv/kv-v2).
2. Store a fresh secret with at least 256 bits of cryptographic randomness at the same mount/path/key.
   Use compare-and-set, preserve unrelated fields and keep values out of logs and shell arguments.
   Back up Vault with the new version and retain the required recovery material separately.
3. Call `POST /api/v1/vault/nodes/update-keys?keyVersion=<source version>` for each version to migrate,
   or `POST /api/v1/vault/nodes/{nodeId}/update-key` for one node. `202 Accepted` confirms submission.
   Check logs, retry skipped or failed nodes, and verify content and metadata readability.
4. Retain old keys while any document, backup, snapshot or stored historical payload needs them.
   Re-encrypting live data does not migrate backups. Before retiring a key, verify an isolated restore
   of matching MongoDB, content storage and Vault history. If dependencies are uncertain, keep it.

Restrict key deletion to Vault administrators. Restore lost keys with their original version mapping;
creating a new secret cannot recover old documents. Docker dev Vault is ephemeral and unsuitable
for durable archives.

## Changing the Encryption Configuration

Content and metadata encryption are enabled independently and are recorded per node, so the configuration can change
only in some directions once data is stored:

- **Enabling encryption** is supported: existing content and metadata stay readable and new nodes are stored
  encrypted. Existing nodes are not re-encrypted.
- **Disabling encryption:** plaintext content remains readable. Encrypted content is still decrypted
  if a crypto service is available; otherwise reading it fails with `IllegalStateException`.
  Encrypted metadata cannot be read with metadata encryption disabled.
- **Changing `impl`, KDF parameters or salt length** affects new writes only. Existing ANV v1
  payloads are decrypted using their authenticated header. Nonces must be 12 bytes.
- Changing these settings does not migrate or re-encrypt existing data. Key rotation
  still selects nodes by secret version; a current key version does not trigger a format or algorithm migration.

## Development Recommendations

1. **Testing**: Test both implementations to find which performs better in your environment

2. **Debugging**: Enable debug logging for encryption services to diagnose issues

3. **Security Reviews**: Consider a security review of your configuration before production deployment

4. **Parameter Tuning**: Adjust KDF parameters based on your security/performance requirements

## References

- [NIST SP 800-38D: Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM)](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf)
- [RFC 7539: ChaCha20 and Poly1305 for IETF Protocols](https://tools.ietf.org/html/rfc7539)
- [RFC 2898: PBKDF2 Specification](https://tools.ietf.org/html/rfc2898)
- [Argon2: The Password Hash](https://github.com/P-H-C/phc-winner-argon2)
- [RFC 5869: HMAC-based Extract-and-Expand Key Derivation Function (HKDF)](https://tools.ietf.org/html/rfc5869)
