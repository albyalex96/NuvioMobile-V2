package com.nuvio.app.features.downloads

internal expect object DownloadsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)

    /** Season downloads of the active profile (see SeasonDownloadRepository). */
    fun loadSeasonBatchesPayload(): String?
    fun saveSeasonBatchesPayload(payload: String)

    /** How many episodes of the season downloads run at the same time; null = default. */
    fun loadSeasonConcurrency(): Int?
    fun saveSeasonConcurrency(value: Int)

    /** Save episodes in "Series/Season" folders; null = default. */
    fun loadOrganizeEpisodeFolders(): Boolean?
    fun saveOrganizeEpisodeFolders(value: Boolean)
}
