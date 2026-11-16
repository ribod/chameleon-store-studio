package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Strategy algorithms applied when validating screenshots against historical baselines.
 */
@Serializable
public enum class BaselineComparison {
    /**
     * Strict cryptographic comparison enforcing identical raw uncompressed pixel SHA-256 hashes.
     */
    STRICT,

    /**
     * Perceptual visual comparison using 64-bit DCT cosine transforms and Hamming distance.
     *
     * Tolerates minor sub-pixel rasterization nuances across differing OS architectures. Changes in
     * the captured text always count as drift.
     */
    PERCEPTUAL,
}
