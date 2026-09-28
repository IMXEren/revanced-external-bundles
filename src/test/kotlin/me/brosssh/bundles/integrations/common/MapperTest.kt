package me.brosssh.bundles.integrations.common

import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.BundleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MapperTest {
    @Test
    fun `choosePatchBundle prefers explicit assets and returns null without a bundle`() {
        val jar = AssetInfo("legacy.jar", "https://example.com/legacy.jar", null)
        val rvp = AssetInfo("patches.rvp", "https://example.com/patches.rvp", null)

        assertEquals(rvp to BundleType.REVANCED_V4, listOf(jar, rvp).choosePatchBundle())
        assertEquals(jar to BundleType.REVANCED_V3, listOf(jar).choosePatchBundle())
        assertNull(listOf(AssetInfo("notes.txt", "https://example.com/notes.txt", null)).choosePatchBundle())
    }

    private fun release(vararg assets: AssetInfo) = ReleaseInfo(
        tagName = "v1.0",
        body = "Release notes",
        prerelease = false,
        createdAt = "2025-01-01T00:00:00Z",
        assets = assets.toList()
    )

    @Test
    fun `explicit rvp asset takes priority over an earlier jar`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", "jar-digest"),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", "rvp-digest")
        ).toDomainModel(7)

        assertEquals(BundleType.REVANCED_V4, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.rvp", metadata.bundle.downloadUrl)
        assertEquals("rvp-digest", metadata.fileHash)
        assertEquals(7, metadata.bundle.sourceFk)
    }

    @Test
    fun `explicit mpp asset in URL takes priority over an earlier jar`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("Patches", "https://example.com/patches.MPP?download=1#file", "mpp-digest")
        ).toDomainModel(7)

        assertEquals(BundleType.MORPHE_V1, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.MPP?download=1#file", metadata.bundle.downloadUrl)
        assertEquals("mpp-digest", metadata.fileHash)
    }

    @Test
    fun `first explicit asset wins when multiple explicit types are present`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("patches.mpp", "https://example.com/patches.mpp", null),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", null)
        ).toDomainModel(7)

        assertEquals(BundleType.MORPHE_V1, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.mpp", metadata.bundle.downloadUrl)
    }

    @Test
    fun `jar is used when no explicit bundle type is present`() {
        val metadata = release(
            AssetInfo("notes.txt", "https://example.com/notes.txt", null),
            AssetInfo("Patches", "https://example.com/patches.JAR?download=1", "jar-digest")
        ).toDomainModel(7)

        assertEquals(BundleType.REVANCED_V3, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.JAR?download=1", metadata.bundle.downloadUrl)
        assertEquals("jar-digest", metadata.fileHash)
    }

    @Test
    fun `release without a bundle asset is rejected`() {
        assertFailsWith<BundleImportError.ReleaseFileNotFoundError> {
            release(AssetInfo("signature.asc", "https://example.com/signature.asc", null))
                .toDomainModel(7)
        }
    }

    @Test
    fun `signature is retained when selecting a later explicit bundle asset`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("signature.asc", "https://example.com/signature.asc", null),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", null)
        ).toDomainModel(7)

        assertEquals("https://example.com/signature.asc", metadata.bundle.signatureDownloadUrl)
        assertNull(metadata.fileHash)
    }
}
