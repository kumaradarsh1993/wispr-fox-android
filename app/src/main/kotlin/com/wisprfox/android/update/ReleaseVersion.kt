package com.wisprfox.android.update

/** Small SemVer comparator that keeps nightly sequence information intact. */
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channel: String?,
    val sequence: Int,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)
            .takeIf { it != 0 }?.let { return it }
        // A final release is newer than a prerelease with the same base.
        if (channel == null && other.channel != null) return 1
        if (channel != null && other.channel == null) return -1
        compareValues(channel.orEmpty(), other.channel.orEmpty()).takeIf { it != 0 }?.let { return it }
        return sequence.compareTo(other.sequence)
    }

    companion object {
        private val pattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z]+)(?:\\.(\\d+))?)?.*$")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            return ReleaseVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                channel = match.groupValues[4].lowercase().takeIf(String::isNotBlank),
                sequence = match.groupValues[5].toIntOrNull() ?: 0,
            )
        }
    }
}

fun isReleaseNewer(candidate: String?, installed: String): Boolean {
    val candidateVersion = candidate?.let(ReleaseVersion::parse) ?: return false
    val installedVersion = ReleaseVersion.parse(installed) ?: return false
    return candidateVersion > installedVersion
}
