# Encryption Options Documentation

## Overview

This document outlines the encryption capabilities available in the Alfresco Node Vault application. The system provides two alternative encryption implementations:

1. **JCA Implementation** - Using AES-GCM
2. **Bouncy Castle Implementation** - Using ChaCha20-Poly1305

Both implementations provide authenticated encryption with strong security guarantees but offer different performance characteristics and compatibility options.

## Configuration

The encryption service can be configured in your `application.yml` file:

```yaml
application:
  service:
    vault:
      encryption:
        # Enable/disable encryption functionality
        enabled: ${VAULT_ENCRYPTION_ENABLED:true}
        # Enable/disable metadata encryption
        metadata: ${VAULT_ENCRYPT_METADATA:true}
        # Master encryption key
        secret: ${VAULT_ENCRYPTION_SECRET:changeme}
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

## Key Derivation Functions (KDFs)

The system supports multiple key derivation functions for deriving encryption keys from the master secret:

### PBKDF2 (Default)

**Configuration**: `kdf.type: "pbkdf2"`

PBKDF2 (Password-Based Key Derivation Function 2) is a widely supported KDF that applies a pseudorandom function to derive keys.

- **Pros**: Widely supported, FIPS-compliant, simple parameters
- **Cons**: More vulnerable to hardware acceleration attacks than newer alternatives
- **Recommended Settings**:
    - iterations: 100,000 minimum (higher values increase security but reduce performance)

### Argon2

**Configuration**: `kdf.type: "argon2"`

Argon2 is a modern KDF designed to be resistant to GPU, ASIC, and side-channel attacks. It won the Password Hashing Competition in 2015.

- **Pros**: Strong resistance to parallel attacks, memory-hardness deters hardware acceleration
- **Cons**: Less widely supported than PBKDF2, use more more memory
- **Recommended Settings**:
    - memory: 65536 (64MB) or higher
    - parallelism: 4 (adjust based on available CPU cores)
    - iterations-argon: 3-4

### HKDF

**Configuration**: `kdf.type: "hkdf"`

HKDF (HMAC-based Key Derivation Function) is designed for deriving keys from already-strong input keying material.

- **Pros**: Very efficient for high-quality input keys
- **Cons**: Not designed for password-based key derivation
- **Use Case**: Best when your master key is already highly random, not for password-based scenarios

## Encryption Implementations

### JCA Implementation (AES-GCM)

**Configuration**: `impl: "jca"`

Uses the Java Cryptography Architecture with AES in Galois/Counter Mode.

- **Algorithm**: AES-GCM with 256-bit keys and 128-bit authentication tag
- **Pros**:
    - Hardware acceleration on modern CPUs with AES-NI
    - Widely supported in all Java environments
    - FIPS-compliant
- **Cons**:
    - More vulnerable to timing attacks if implemented improperly
    - Performance varies based on hardware support
- **Recommended Settings**:
    - salt-length: 16 bytes (128 bits)
    - iv-length: 12 bytes (96 bits) - GCM optimal IV size

### Bouncy Castle Implementation (ChaCha20-Poly1305)

**Configuration**: `impl: "bc"`

Uses the Bouncy Castle provider to implement ChaCha20-Poly1305 authenticated encryption.

- **Algorithm**: ChaCha20 stream cipher with Poly1305 authenticator
- **Pros**:
    - Excellent performance on platforms without AES hardware acceleration
    - More resistant to timing attacks
    - Designed for software implementation
- **Cons**:
    - Requires additional Bouncy Castle provider
    - Not FIPS-compliant in most environments
- **Recommended Settings**:
    - salt-length: 16 bytes (128 bits)
    - nonce-length: 12 bytes (96 bits) - ChaCha20-Poly1305 standard

## Security Considerations

1. **Storage of Master Secret**:
    - The master secret should be stored securely using environment variables or a secrets manager
    - Never commit secrets to source control

2. **Nonce/IV Usage**:
    - Each encryption operation uses a new random nonce/IV
    - The system prepends the salt and nonce/IV to the encrypted data for decryption

5. **Processing Model**:
    - The system uses streaming encryption/decryption to handle large files efficiently
    - Minimal memory footprint even with large files

## Performance Considerations

- **ChaCha20-Poly1305 (BC)** typically performs better on:
    - Older hardware without AES-NI
    - ARM and RISC-V CPUs

- **AES-GCM (JCA)** typically performs better on:
    - Modern x86 servers with AES-NI
    - Cloud environments with hardware-accelerated AES

## Format of Encrypted Data

Both implementations produce output with the following structure:

```
[salt][nonce/iv][encrypted data]
```

Where:
- `salt` is used for key derivation (length defined by configuration)
- `nonce/iv` is used for the encryption algorithm (length defined by configuration)
- `encrypted data` includes authentication data (GCM tag or Poly1305 authenticator)

This format ensures that all information needed for decryption (except the master secret) is contained within the encrypted data itself.

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