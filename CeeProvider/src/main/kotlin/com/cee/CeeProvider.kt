package com.cee

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

class CeeProvider : MainAPI() {

    override var mainUrl = "https://cee.buzz"
    override var name = "Cee"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "ar"
    override val hasMainPage = true

    private val apiBase get() = "$mainUrl/api/android"
    private val itemsPerPageSearch = 20

    override val mainPage = mainPageOf(
        "$apiBase/newlyVideosItems/level/0/offset/12/page/" to "أحدث الإضافات",

        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=desc" to "أفلام - تاريخ الرفع - الأحدث",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=asc" to "أفلام - تاريخ الرفع - الأقدم",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=views_desc" to "أفلام - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=stars_desc" to "أفلام - أعلى تقييم IMDb",

        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=desc" to "مسلسلات - تاريخ الرفع - الأحدث",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=asc" to "مسلسلات - تاريخ الرفع - الأقدم",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=views_desc" to "مسلسلات - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=stars_desc" to "مسلسلات - أعلى تقييم IMDb",
    )

    data class CinemanaItem(
        @JsonProperty("nb") val nb: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("en_title") val enTitle: String?,
        @JsonProperty("ar_title") val arTitle: String?,
        @JsonProperty("en_content") val enContent: String?,
        @JsonProperty("ar_content") val arContent: String?,
        @JsonProperty("stars") val stars: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("imgObjUrl") val imgObjUrl: String?,
        @JsonProperty("linkName") val linkName: String?,
        @JsonProperty("actorsInfo") val actorsInfo: List<ActorInfo>?,
        @JsonProperty("categories") val categories: List<Category>?,
        @JsonProperty("episodeNummer") val episodeNummer: Int?,
        @JsonProperty("season") val season: Int?
    )

    data class ActorInfo(
        @JsonProperty("nb") val nb: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("role") val role: String?,
        @JsonProperty("staff_img") val staffImg: String?,
        @JsonProperty("staff_img_medium_thumb") val staffImgMediumThumb: String?,
        @JsonProperty("staff_img_thumb") val staffImgThumb: String?
    )

    data class Category(
        @JsonProperty("en_title") val enTitle: String?,
        @JsonProperty("ar_title") val arTitle: String?
    )

    data class VideoListResponse(
        @JsonProperty("items") val items: List<CinemanaItem>?,
        @JsonProperty("hasMore") val hasMore: Boolean?
    )

    data class VideoFile(
        @JsonProperty("videoUrl") val videoUrl: String?,
        @JsonProperty("resolution") val resolution: String?,
        @JsonProperty("fileFile") val fileFile: String?
    )

    data class VideoFilesResponse(
        @JsonProperty("items") val items: List<VideoFile>?
    )

    data class VideoGroup(
        @JsonProperty("id") val id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("en_title") val enTitle: String?,
        @JsonProperty("ar_title") val arTitle: String?
    )

    private fun parseVideoListResponse(text: String): VideoListResponse {
        tryParseJson<VideoListResponse>(text)?.let { return it }

        tryParseJson<List<CinemanaItem>>(text)?.let { list ->
            return VideoListResponse(items = list, hasMore = list.isNotEmpty())
        }

        tryParseJson<List<Map<String, Any>>>(text)?.let { list ->
            return VideoListResponse(
                items = list.mapNotNull { it.toCinemanaItem() },
                hasMore = list.isNotEmpty()
            )
        }

        return VideoListResponse(emptyList(), false)
    }

    private fun parseVideoFiles(text: String): List<VideoFile> {
        tryParseJson<VideoFilesResponse>(text)?.items?.let { return it }
        tryParseJson<List<VideoFile>>(text)?.let { return it }

        tryParseJson<List<Map<String, Any>>>(text)?.let { list ->
            return list.mapNotNull { it.toVideoFile() }
        }

        return emptyList()
    }

    private fun CinemanaItem.toSearchResponse(): SearchResponse? {
        val id = nb?.takeIf { it.isNotBlank() } ?: return null

        val label = enTitle?.takeIf { it.isNotBlank() }
            ?: arTitle?.takeIf { it.isNotBlank() }
            ?: title
            ?: return null

        val url = "$mainUrl/details/$id"
        val isSeries = kind?.toIntOrNull() == 2

        return if (isSeries) {
            newTvSeriesSearchResponse(label, url, TvType.TvSeries) {
                posterUrl = imgObjUrl
                this.year = this@toSearchResponse.year
            }
        } else {
            newMovieSearchResponse(label, url, TvType.Movie) {
                posterUrl = imgObjUrl
                this.year = this@toSearchResponse.year
            }
        }
    }

    private fun CinemanaItem.toActors(): List<ActorData>? =
        actorsInfo?.mapNotNull { actor ->
            val actorName =
                actor.title?.takeIf { it.isNotBlank() }
                    ?: actor.name?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

            val image = actor.staffImgMediumThumb ?: actor.staffImg

            ActorData(
                Actor(actorName, image),
                roleString = actor.role
            )
        }?.takeIf { it.isNotEmpty() }

    private fun CinemanaItem.toTags(): List<String>? =
        categories?.mapNotNull { cat ->
            cat.arTitle?.takeIf { it.isNotBlank() } ?: cat.enTitle
        }?.takeIf { it.isNotEmpty() }

    private fun Map<String, Any>.toCinemanaItem(): CinemanaItem? {
        val parsedNb = when (val nbValue = this["nb"]) {
            is String -> nbValue
            is Int -> nbValue.toString()
            is Double -> nbValue.toLong().toString()
            is Long -> nbValue.toString()
            else -> null
        }

        val categoriesParsed = (this["categories"] as? List<*>)?.mapNotNull { c ->
            (c as? Map<*, *>)?.let { m ->
                Category(
                    enTitle = m["en_title"] as? String,
                    arTitle = m["ar_title"] as? String
                )
            }
        }

        val actorsParsed = (this["actorsInfo"] as? List<*>)?.mapNotNull { a ->
            (a as? Map<*, *>)?.let { m ->
                ActorInfo(
                    nb = m["nb"]?.toString(),
                    title = m["title"] as? String,
                    name = m["name"] as? String,
                    role = m["role"] as? String,
                    staffImg = m["staff_img"] as? String,
                    staffImgMediumThumb = m["staff_img_medium_thumb"] as? String,
                    staffImgThumb = m["staff_img_thumb"] as? String
                )
            }
        }

        return CinemanaItem(
            nb = parsedNb,
            title = this["title"] as? String,
            enTitle = this["en_title"] as? String,
            arTitle = this["ar_title"] as? String,
            enContent = this["en_content"] as? String,
            arContent = this["ar_content"] as? String,
            stars = this["stars"]?.toString(),
            year = (this["year"] as? Number)?.toInt()
                ?: (this["year"] as? String)?.toIntOrNull(),
            kind = this["kind"]?.toString(),
            imgObjUrl = this["imgObjUrl"] as? String ?: this["img"] as? String,
            linkName = this["linkName"] as? String,
            actorsInfo = actorsParsed,
            categories = categoriesParsed,
            episodeNummer = (this["episodeNummer"] as? Number)?.toInt()
                ?: (this["episodeNummer"] as? String)?.toIntOrNull(),
            season = (this["season"] as? Number)?.toInt()
                ?: (this["season"] as? String)?.toIntOrNull()
        )
    }

    private fun Map<String, Any>.toVideoFile(): VideoFile? {
        val videoUrl = this["videoUrl"] as? String ?: return null

        return VideoFile(
            videoUrl = videoUrl,
            resolution = this["resolution"] as? String,
            fileFile = this["fileFile"] as? String
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"

        return try {
            val parsed = parseVideoListResponse(app.get(url).text)
            val items = parsed.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()

            newHomePageResponse(
                listOf(
                    HomePageList(
                        request.name,
                        items,
                        isHorizontalImages = true
                    )
                ),
                hasNext = parsed.hasMore ?: false
            )
        } catch (e: Exception) {
            logError(e)
            newHomePageResponse(emptyList(), false)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = URLEncoder.encode(query, "utf-8")
        val pageParam = (page - 1).coerceAtLeast(0)

        val currentYear = java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.YEAR)

        val yearRange = "1900,$currentYear"

        val moviesUrl =
            "$apiBase/AdvancedSearch?level=0&videoTitle=$encoded&staffTitle=$encoded&year=$yearRange&page=$pageParam&type=movies&itemsPerPage=$itemsPerPageSearch"

        val seriesUrl =
            "$apiBase/AdvancedSearch?level=0&videoTitle=$encoded&staffTitle=$encoded&year=$yearRange&page=$pageParam&type=series&itemsPerPage=$itemsPerPageSearch"

        val moviesRaw = runCatching {
            parseVideoListResponse(app.get(moviesUrl).text).items ?: emptyList()
        }.getOrElse { emptyList() }

        val seriesRaw = runCatching {
            parseVideoListResponse(app.get(seriesUrl).text).items ?: emptyList()
        }.getOrElse { emptyList() }

        val tokens = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        fun scoreItem(item: CinemanaItem): Int {
            val haystack = listOfNotNull(item.enTitle, item.arTitle, item.title)
                .joinToString(" ")
                .lowercase()

            val exact = if (haystack == query.lowercase().trim()) 100 else 0
            val starts = if (haystack.startsWith(query.lowercase().trim())) 80 else 0
            val contains = if (haystack.contains(query.lowercase().trim())) 60 else 0
            val tokenMatches = tokens.count { haystack.contains(it) }

            return maxOf(exact, starts, contains) + tokenMatches
        }

        val combined = (moviesRaw + seriesRaw)
            .distinctBy { it.nb }
            .sortedByDescending { scoreItem(it) }
            .mapNotNull { it.toSearchResponse() }

        return combined.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfterLast("/")
        val detailsUrl = "$apiBase/allVideoInfo/id/$id"

        val item = tryParseJson<CinemanaItem>(app.get(detailsUrl).text)
            ?: return null

        val label = item.enTitle?.takeIf { it.isNotBlank() }
            ?: item.arTitle?.takeIf { it.isNotBlank() }
            ?: item.title
            ?: return null

        val plotText =
            item.arContent?.takeIf { it.isNotBlank() } ?: item.enContent

        val tags = item.toTags()
        val actors = item.toActors()
        val itemYear = item.year

        return if (item.kind?.toIntOrNull() == 2) {

            val seasonsUrl = "$apiBase/videoSeason/id/$id"

            val episodesResponse = runCatching {
                tryParseJson<List<Map<String, Any>>>(
                    app.get(seasonsUrl).text
                ) ?: emptyList()
            }.getOrElse { emptyList() }

            val episodes = mutableListOf<Episode>()
            val seasonsMap = mutableMapOf<Int, MutableList<Episode>>()

            episodesResponse.forEach { episodeMap ->

                val episodeDetails =
                    episodeMap.toCinemanaItem() ?: return@forEach

                val episodeId =
                    episodeDetails.nb ?: return@forEach

                val episodeNum =
                    episodeDetails.episodeNummer ?: 1

                val seasonNum =
                    episodeDetails.season ?: 1

                val newEpisode = newEpisode(episodeId) {
                    name =
                        episodeDetails.title
                            ?: episodeDetails.enTitle
                            ?: "الحلقة $episodeNum"

                    season = seasonNum
                    episode = episodeNum
                    posterUrl =
                        episodeDetails.imgObjUrl ?: item.imgObjUrl

                    description =
                        episodeDetails.enContent
                            ?: episodeDetails.arContent
                }

                seasonsMap
                    .getOrPut(seasonNum) { mutableListOf() }
                    .add(newEpisode)
            }

            val sortedSeasonNumbers =
                seasonsMap.keys.sorted()

            sortedSeasonNumbers.forEach { sNum ->
                val seasonEpisodes = seasonsMap[sNum]
                seasonEpisodes?.sortBy { it.episode }

                if (seasonEpisodes != null) {
                    episodes.addAll(seasonEpisodes)
                }
            }

            newTvSeriesLoadResponse(
                label,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = item.imgObjUrl
                this.plot = plotText
                this.year = itemYear
                this.tags = tags
                this.actors = actors
            }

        } else {

            newMovieLoadResponse(
                label,
                url,
                TvType.Movie,
                id
            ) {
                this.posterUrl = item.imgObjUrl
                this.plot = plotText
                this.year = itemYear
                this.tags = tags
                this.actors = actors
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val extractedId =
            data.substringAfterLast("/")
                .substringBefore("?")

        val videosUrl =
            "$apiBase/transcoddedFiles/id/$extractedId"

        val videoFiles =
            parseVideoFiles(app.get(videosUrl).text)

        if (videoFiles.isEmpty()) return false

        videoFiles.reversed().forEach { file ->

            val videoUrl =
                file.videoUrl?.takeIf { it.isNotBlank() }
                    ?: return@forEach

            val resolution =
                file.resolution?.takeIf { it.isNotBlank() }
                    ?: file.fileFile?.takeIf { it.isNotBlank() }
                    ?: "Unknown"

            val quality =
                getQualityFromName(resolution.substringBefore("p"))

            callback(
                newExtractorLink(
                    source = this.name,
                    name = "${this.name} ($resolution)",
                    url = videoUrl
                ) {
                    this.quality = quality
                    this.referer = mainUrl
                }
            )
        }

        val detailsUrl =
            "$apiBase/allVideoInfo/id/$extractedId"

        runCatching {

            app.get(detailsUrl)
                .parsedSafe<Map<String, Any>>()
                ?.let { detailsMap ->

                    (detailsMap["translations"]
                            as? List<Map<String, Any>>)
                        ?.forEach { sub ->

                            val file = sub["file"] as? String
                            val lang = sub["name"] as? String

                            if (file != null && lang != null) {
                                subtitleCallback(
                                    SubtitleFile(lang, file)
                                )
                            }
                        }
                }
        }

        return true
    }
}