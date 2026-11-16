package dev.ribod.storestudio.core.report

/**
 * Image embedding strategies for standalone HTML reports.
 */
public enum class ReportImageMode {
    /**
     * Downscaled high-efficiency JPEG data URIs for fast rendering and minimal file size,
     * paired with relative links to original full-resolution export files.
     */
    THUMBNAIL,

    /**
     * Original export files encoded directly as Base64 data URIs.
     * Yields full pixel fidelity at the expense of substantially larger HTML document size.
     */
    FULL,

    /**
     * Relative filesystem links (<img src="../export/...">) without Base64 embedding.
     * Produces the smallest HTML size but requires accompanying export assets on disk.
     */
    LINK,
}
