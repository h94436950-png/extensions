package com.cee

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

class CeeProvider : MainAPI() {

    override var mainUrl = "https://cee.buzz"
    override var name = "cee (🇮🇶)"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "ar"
    override val hasMainPage = true

    private val apiBase get() = "$mainUrl/api/android"
    private val itemsPerPageSearch = 30

    override val mainPage = mainPageOf(
        "$apiBase/newlyVideosItems/level/0/offset/12/page/" to "أحدث الإضافات",

        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=desc" to "أفلام - تاريخ الرفع - الأحدث",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=asc" to "أفلام - تاريخ الرفع - الأقدم",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=ar_title_asc" to "أفلام - أبجديًا (أ-ي)",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=ar_title_desc" to "أفلام - أبجديًا (ب-أ)",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=en_title_desc" to "أفلام - أبجديًا (Z-A)",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=en_title_asc" to "أفلام - أبجديًا (A-Z)",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=views_desc" to "أفلام - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=stars_desc" to "أفلام - أعلى تقييم IMDb",

        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=desc" to "مسلسلات - تاريخ الرفع - الأحدث",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=asc" to "مسلسلات - تاريخ الرفع - الأقدم",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=en_title_desc" to "مسلسلات - أبجديًا (أ-ي)",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=en_title_asc" to "مسلسلات - أبجديًا (ي-أ)",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=en_title_desc" to "مسلسلات - أبجديًا (Z-A)",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&pageNumber=&level=0&sortParam=en_title_asc" to "مسلسلات - أبجديًا (A-Z)",
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
        @JsonProperty("year") val year: String?,
        @JsonProperty("kind") val kind: Int?,
        @JsonProperty("imgObjUrl") val imgObjUrl: String?,
        @JsonProperty("linkName") val linkName: String?,
        @JsonProperty("fileFile") val fileFile: String?,
        @JsonProperty("episodeNummer") val episodeNummer: String?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("categories") val categories: List<Category>?,
        @JsonProperty("actorsInfo") val actorsInfo: List<ActorInfo>?
    )

    data class ActorInfo(
        @JsonProperty("nb") val nb: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("role") val role: String?,
        @JsonProperty("staff_img") val staffImg: String?,
        @JsonProperty("staff_img_thumb") val staffImgThumb: String?,
        @JsonProperty("staff_img_medium_thumb") val staffImgMediumThumb: String?
    )

    data class Category(
        @JsonProperty("en_title") val enTitle: String?,
        @JsonProperty("ar_title") val arTitle: String?
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
        @JsonProperty("title") val title: String?
    )

    private fun parseVideoFiles(text: String): List<VideoFile> {
        tryParseJson<VideoFilesResponse>(text)?.items?.let { return it }
        tryParseJson<List<VideoFile>>(text)?.let { return it }

        tryParseJson<List<Map<String, Any>>>(text)?.let { list ->
            return list.mapNotNull { it.toVideoFile() }
        }

        return emptyList()
    }

    private fun Map<String, Any>.toVideoFile(): VideoFile? {
        val videoUrl = this["videoUrl"] as? String ?: return null

        return VideoFile(
            videoUrl = videoUrl,
            resolution = this["resolution"] as? String,
            fileFile = this["fileFile"] as? String
        )
    }

    private fun Map<String, Any>.toCinemanaItem(): CinemanaItem {

        val parsedNb = when (val nbValue = this["nb"]) {
            is String -> nbValue
            is Int -> nbValue.toString()
            is Double -> nbValue.toLong().toString()
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
                    name = m["name"] as? String,
                    role = m["role"] as? String,
                    staffImg = m["staff_img"] as? String,
                    staffImgThumb = m["staff_img_thumb"] as? String,
                    staffImgMediumThumb = m["staff_img_medium_thumb"] as? String
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
            year = this["year"]?.toString(),
            kind = (this["kind"] as? String)?.toIntOrNull()
                ?: (this["kind"] as? Int),
            imgObjUrl = this["imgObjUrl"] as? String
                ?: this["img"] as? String,
            linkName = this["linkName"] as? String,
            fileFile = this["fileFile"] as? String,
            episodeNummer = this["episodeNummer"]?.toString(),
            season = this["season"]?.toString(),
            categories = categoriesParsed,
            actorsInfo = actorsParsed
        )
    }

    private fun CinemanaItem.toSearchResponse(): SearchResponse? {

        val validNb = nb ?: return null

        val rating = stars?.toFloatOrNull()
        val scoreObject = rating?.let { Score.from10(it) }

        return if (kind == 2) {

            newTvSeriesSearchResponse(
                name = enTitle ?: arTitle ?: title ?: "No Title",
                url = "$mainUrl/details/$validNb",
                type = TvType.TvSeries
            ) {
                this.posterUrl = imgObjUrl
                this.score = scoreObject
            }

        } else {

            newMovieSearchResponse(
                name = enTitle ?: arTitle ?: title ?: "No Title",
                url = "$mainUrl/details/$validNb",
                type = TvType.Movie
            ) {
                this.posterUrl = imgObjUrl
                this.score = scoreObject
            }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val items = mutableListOf<HomePageList>()
        var hasMore = false

        val requestData = request.data ?: ""

        if (requestData.isNotBlank()) {

            val fetchUrl = when {

                requestData.contains("/page/") -> {
                    if (requestData.endsWith("/page/"))
                        "$requestData$page/"
                    else
                        requestData.replace(
                            Regex("/page/\\d+/?$"),
                            "/page/$page/"
                        )
                }

                requestData.contains("pageNumber=") -> {

                    val zeroBasedPage = page - 1

                    val replaced = requestData.replace(
                        Regex("pageNumber=\\d*"),
                        "pageNumber=$zeroBasedPage"
                    )

                    if (replaced == requestData) {

                        if (requestData.contains("?"))
                            "$requestData&pageNumber=$zeroBasedPage"
                        else
                            "$requestData?pageNumber=$zeroBasedPage"

                    } else replaced
                }

                else -> {
                    if (requestData.endsWith("/"))
                        "$requestData$page/"
                    else
                        "$requestData/$page/"
                }
            }

            val resp = runCatching {
                app.get(fetchUrl).parsedSafe<List<Map<String, Any>>>()
            }.getOrNull()

            val parsed = resp
                ?.mapNotNull { it.toCinemanaItem().toSearchResponse() }
                ?: emptyList()

            items.add(
                HomePageList(
                    request.name ?: "القسم",
                    parsed
                )
            )

            val rawSize = resp?.size ?: parsed.size
            hasMore = rawSize >= 12

            return newHomePageResponse(items, hasNext = hasMore)
        }

        return newHomePageResponse(emptyList(), false)
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse>? {

        return search(query, 1)?.items
    }

    override suspend fun search(
        query: String,
        page: Int
    ): SearchResponseList? {

        val encoded = URLEncoder.encode(query, "utf-8")

        val currentYear = java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.YEAR)

        val yearRange = "1900,$currentYear"

        val pageParam = (page - 1).coerceAtLeast(0)

        val moviesUrl =
            "$apiBase/AdvancedSearch?level=0&videoTitle=$encoded&staffTitle=$encoded&year=$yearRange&page=$pageParam&type=movies&itemsPerPage=$itemsPerPageSearch"

        val seriesUrl =
            "$apiBase/AdvancedSearch?level=0&videoTitle=$encoded&staffTitle=$encoded&year=$yearRange&page=$pageParam&type=series&itemsPerPage=$itemsPerPageSearch"

        val moviesRaw = runCatching {

            app.get(moviesUrl)
                .parsedSafe<List<Map<String, Any>>>()
                ?: emptyList()

        }.getOrElse { emptyList() }

        val seriesRaw = runCatching {

            app.get(seriesUrl)
                .parsedSafe<List<Map<String, Any>>>()
                ?: emptyList()

        }.getOrElse { emptyList() }

        val movies = moviesRaw.mapNotNull {
            it.toCinemanaItem().toSearchResponse()
        }

        val series = seriesRaw.mapNotNull {
            it.toCinemanaItem().toSearchResponse()
        }

        val maxSize = maxOf(movies.size, series.size)

        val interleaved =
            mutableListOf<SearchResponse>()

        for (i in 0 until maxSize) {

            if (i < movies.size)
                interleaved.add(movies[i])

            if (i < series.size)
                interleaved.add(series[i])
        }

        fun scoreMatch(
            title: String?,
            q: String
        ): Int {

            if (title.isNullOrBlank())
                return 0

            val t = title.lowercase()
            val ql = q.lowercase().trim()

            if (t == ql) return 100
            if (t.startsWith(ql)) return 80
            if (t.contains(ql)) return 60

            val tokens =
                ql.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

            val tokenMatches =
                tokens.count { t.contains(it) }

            return 40 + tokenMatches
        }

        val sorted = interleaved
            .mapIndexed { idx, item ->

                val titleCandidate =
                    item.name ?: item.url ?: ""

                val score =
                    scoreMatch(titleCandidate, query)

                Triple(item, score, idx)
            }
            .sortedWith(
                compareByDescending<Triple<SearchResponse, Int, Int>> {
                    it.second
                }.thenBy {
                    it.third
                }
            )
            .map {
                it.first
            }

        val finalResults =
            sorted.distinctBy {
                "${it.url}-${it.name}"
            }

        val hasMore =
            interleaved.isNotEmpty()

        return newSearchResponseList(
            finalResults,
            hasMore
        )
    }

    private fun CinemanaItem.toActors(): List<ActorData>? {

        return actorsInfo?.mapNotNull { actor ->

            val actorName =
                actor.name?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

            val image =
                actor.staffImgMediumThumb
                    ?: actor.staffImg
                    ?: actor.staffImgThumb

            ActorData(
                actor = Actor(
                    name = actorName,
                    image = image
                ),
                roleString = actor.role
            )

        }?.takeIf { it.isNotEmpty() }
    }

    private fun CinemanaItem.toTags(): List<String>? {

        return categories?.mapNotNull { cat ->
            cat.arTitle?.takeIf { it.isNotBlank() }
                ?: cat.enTitle
        }?.distinct()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun load(url: String): LoadResponse? {

        val extractedId = url.substringAfterLast("/")

        val detailsUrl =
            "$apiBase/allVideoInfo/id/$extractedId"

        val detailsMap =
            app.get(detailsUrl)
                .parsedSafe<Map<String, Any>>()
                ?: return null

        val details =
            detailsMap.toCinemanaItem()

        val title =
            details.enTitle
                ?: details.arTitle
                ?: details.title
                ?: return null

        val posterUrl = details.imgObjUrl

        val plot =
            details.arContent
                ?: details.enContent

        val year =
            details.year?.toIntOrNull()

        val rating =
            details.stars?.toFloatOrNull()

        val score =
            rating?.let { Score.from10(it) }

        val tags =
            details.toTags() ?: emptyList()

        val actors =
            details.toActors() ?: emptyList()

        return if (details.kind == 2) {

            val seasonsUrl =
                "$apiBase/videoSeason/id/$extractedId"

            val episodesResponse =
                app.get(seasonsUrl)
                    .parsedSafe<List<Map<String, Any>>>()

            val episodes = mutableListOf<Episode>()
            val seasonsMap =
                mutableMapOf<Int, MutableList<Episode>>()

            episodesResponse?.forEach { episodeMap ->

                val episodeDetails =
                    episodeMap.toCinemanaItem()

                val episodeId =
                    episodeDetails.nb ?: return@forEach

                val episodeNum =
                    episodeDetails.episodeNummer
                        ?.toIntOrNull() ?: 1

                val seasonNum =
                    episodeDetails.season
                        ?.toIntOrNull() ?: 1

                val newEpisode = newEpisode(episodeId) {

                    this.name =
                        episodeDetails.title
                            ?: episodeDetails.enTitle
                            ?: "الحلقة $episodeNum"

                    this.season = seasonNum
                    this.episode = episodeNum

                    this.posterUrl =
                        episodeDetails.imgObjUrl
                            ?: posterUrl

                    this.description =
                        episodeDetails.enContent
                            ?: episodeDetails.arContent
                }

                seasonsMap
                    .getOrPut(seasonNum) {
                        mutableListOf()
                    }
                    .add(newEpisode)
            }

            seasonsMap.keys.sorted().forEach { sNum ->

                val seasonEpisodes =
                    seasonsMap[sNum]

                seasonEpisodes?.sortBy { it.episode }

                if (seasonEpisodes != null) {
                    episodes.addAll(seasonEpisodes)
                }
            }

            newTvSeriesLoadResponse(
                title,
                extractedId,
                TvType.TvSeries,
                episodes
            ) {

                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
                this.score = score

                if (tags.isNotEmpty())
                    this.tags = tags

                if (actors.isNotEmpty())
                    this.actors = actors
            }

        } else {

            newMovieLoadResponse(
                title,
                extractedId,
                TvType.Movie,
                extractedId
            ) {

                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
                this.score = score

                if (tags.isNotEmpty())
                    this.tags = tags

                if (actors.isNotEmpty())
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

        if (videoFiles.isEmpty()) {
            return false
        }

        videoFiles.reversed().forEach { file ->

            val videoUrl =
                file.videoUrl?.takeIf { it.isNotBlank() }
                    ?: return@forEach

            val resolution =
                file.resolution?.takeIf { it.isNotBlank() }
                    ?: file.fileFile?.takeIf { it.isNotBlank() }
                    ?: "Unknown"

            callback(
                newExtractorLink(
                    source = name,
                    name = resolution,
                    url = videoUrl
                ) {
                    quality =
                        getQualityFromName(resolution)

                    referer = mainUrl
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

                            val file =
                                sub["file"] as? String

                            val lang =
                                sub["name"] as? String

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