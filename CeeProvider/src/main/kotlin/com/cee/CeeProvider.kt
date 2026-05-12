package com.cee

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class CeeProvider : MainAPI() {

    override var mainUrl = "https://cee.buzz"
    override var name = "Cee"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "ar"
    override val hasMainPage = true

    private val apiBase get() = "$mainUrl/api/android"
    private val itemsPerPageSearch = 20

    override val mainPage = mainPageOf(
        "$mainUrl/newlyVideosItems/level/0/offset/12/page/" to "جديد",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&level=0&sortParam=views_desc&pageNumber=" to "أفلام - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&level=0&sortParam=views_desc&pageNumber=" to "مسلسلات - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&level=0&sortParam=stars_desc&pageNumber=" to "أفلام - الأعلى تقييماً",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&level=0&sortParam=stars_desc&pageNumber=" to "مسلسلات - الأعلى تقييماً",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&level=0&sortParam=asc&pageNumber=" to "أفلام - الأحدث",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&level=0&sortParam=asc&pageNumber=" to "مسلسلات - الأحدث",
    )

    data class CinemanaItem(
        @JsonProperty("nb") val nb: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("ar_title") val arTitle: String?,
        @JsonProperty("en_content") val enContent: String?,
        @JsonProperty("ar_content") val arContent: String?,
        @JsonProperty("stars") val stars: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("imgObjUrl") val imgObjUrl: String?,
        @JsonProperty("linkName") val linkName: String?,
        @JsonProperty("actorsInfo") val actorsInfo: List<ActorInfo>?,
        @JsonProperty("categories") val categories: List<Category>?
    )

    data class ActorInfo(
        @JsonProperty("nb") val nb: String?,
        @JsonProperty("title") val title: String?,
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

    data class SeasonNumberItem(
        @JsonProperty("season") val season: Int?,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>?
    )

    data class EpisodeItem(
        @JsonProperty("nb") val nb: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("episodeNummer") val episodeNummer: Int?,
        @JsonProperty("imgObjUrl") val imgObjUrl: String?
    )

    data class VideoFile(
        @JsonProperty("videoUrl") val videoUrl: String?,
        @JsonProperty("resolution") val resolution: String?,
        @JsonProperty("fileFile") val fileFile: String?
    )

    private fun parseCinemanaList(text: String): List<CinemanaItem> {
        tryParseJson<VideoListResponse>(text)?.items?.let { return it }
        tryParseJson<List<CinemanaItem>>(text)?.let { return it }

        val listAsMaps = tryParseJson<List<Map<String, Any>>>(text)
        if (!listAsMaps.isNullOrEmpty()) return listAsMaps.mapNotNull { it.toCinemanaItem() }

        return emptyList()
    }

    private fun parseCinemanaItem(text: String): CinemanaItem? {
        tryParseJson<CinemanaItem>(text)?.let { return it }
        tryParseJson<Map<String, Any>>(text)?.let { return it.toCinemanaItem() }
        return null
    }

    private fun parseSeasonList(text: String): List<SeasonNumberItem> {
        tryParseJson<List<SeasonNumberItem>>(text)?.let { return it }
        val listAsMaps = tryParseJson<List<Map<String, Any>>>(text)
        if (!listAsMaps.isNullOrEmpty()) return listAsMaps.mapNotNull { it.toSeasonNumberItem() }
        return emptyList()
    }

    private fun parseVideoFiles(text: String): List<VideoFile> {
        tryParseJson<List<VideoFile>>(text)?.let { return it }
        val listAsMaps = tryParseJson<List<Map<String, Any>>>(text)
        if (!listAsMaps.isNullOrEmpty()) return listAsMaps.mapNotNull { it.toVideoFile() }
        return emptyList()
    }

    private fun Map<String, Any>.toCinemanaItem(): CinemanaItem? {
        val categories = (this["categories"] as? List<*>)?.mapNotNull { c ->
            (c as? Map<*, *>)?.let { m ->
                Category(
                    enTitle = m["en_title"] as? String,
                    arTitle = m["ar_title"] as? String
                )
            }
        }

        val actors = (this["actorsInfo"] as? List<*>)?.mapNotNull { a ->
            (a as? Map<*, *>)?.let { m ->
                ActorInfo(
                    nb = m["nb"]?.toString(),
                    title = m["title"] as? String ?: m["name"] as? String,
                    role = m["role"] as? String,
                    staffImg = m["staff_img"] as? String,
                    staffImgMediumThumb = m["staff_img_medium_thumb"] as? String,
                    staffImgThumb = m["staff_img_thumb"] as? String
                )
            }
        }

        val nb = this["nb"]?.toString() ?: return null

        return CinemanaItem(
            nb = nb,
            title = this["title"] as? String,
            arTitle = this["ar_title"] as? String,
            enContent = this["en_content"] as? String,
            arContent = this["ar_content"] as? String,
            stars = this["stars"]?.toString(),
            year = (this["year"] as? Number)?.toInt() ?: (this["year"] as? String)?.toIntOrNull(),
            kind = this["kind"]?.toString(),
            imgObjUrl = this["imgObjUrl"] as? String ?: this["img"] as? String,
            linkName = this["linkName"] as? String,
            actorsInfo = actors,
            categories = categories
        )
    }

    private fun Map<String, Any>.toSeasonNumberItem(): SeasonNumberItem? {
        val episodes = (this["episodes"] as? List<*>)?.mapNotNull { e ->
            (e as? Map<*, *>)?.let { m ->
                EpisodeItem(
                    nb = m["nb"]?.toString(),
                    title = m["title"] as? String,
                    episodeNummer = (m["episodeNummer"] as? Number)?.toInt()
                        ?: (m["episodeNummer"] as? String)?.toIntOrNull(),
                    imgObjUrl = m["imgObjUrl"] as? String
                )
            }
        }

        return SeasonNumberItem(
            season = (this["season"] as? Number)?.toInt() ?: (this["season"] as? String)?.toIntOrNull(),
            episodes = episodes
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

    private fun CinemanaItem.toSearchResponse(): SearchResponse? {
        val id = nb?.takeIf { it.isNotBlank() } ?: return null
        val label = arTitle?.takeIf { it.isNotBlank() } ?: title ?: return null
        val type = if (kind == "2") TvType.TvSeries else TvType.Movie
        val url = "$mainUrl/details/$id"

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(label, url, type) {
                posterUrl = imgObjUrl
                this.year = this@toSearchResponse.year
            }
        } else {
            newMovieSearchResponse(label, url, type) {
                posterUrl = imgObjUrl
                this.year = this@toSearchResponse.year
            }
        }
    }

    private fun CinemanaItem.toActors(): List<ActorData>? =
        actorsInfo?.mapNotNull { actor ->
            val actorName = actor.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val image = actor.staffImgMediumThumb ?: actor.staffImg
            ActorData(Actor(actorName, image), roleString = actor.role)
        }?.takeIf { it.isNotEmpty() }

    private fun CinemanaItem.toTags(): List<String>? =
        categories?.mapNotNull {
            it.arTitle?.takeIf { ar -> ar.isNotBlank() } ?: it.enTitle
        }?.takeIf { it.isNotEmpty() }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        return try {
            val items = parseCinemanaList(app.get(url).text).mapNotNull { it.toSearchResponse() }
            newHomePageResponse(
                listOf(HomePageList(request.name, items, isHorizontalImages = true)),
                hasNext = items.isNotEmpty()
            )
        } catch (e: Exception) {
            logError(e)
            newHomePageResponse(emptyList(), false)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val enc = query.encodeUri()
        val pageParam = page - 1

        val moviesUrl =
            "$mainUrl/AdvancedSearch?level=0&videoTitle=$enc&staffTitle=$enc&type=movies&itemsPerPage=$itemsPerPageSearch&pageNumber=$pageParam&year="
        val seriesUrl =
            "$mainUrl/AdvancedSearch?level=0&videoTitle=$enc&staffTitle=$enc&type=series&itemsPerPage=$itemsPerPageSearch&pageNumber=$pageParam&year="

        val moviesRaw = runCatching { parseCinemanaList(app.get(moviesUrl).text) }.getOrElse { emptyList() }
        val seriesRaw = runCatching { parseCinemanaList(app.get(seriesUrl).text) }.getOrElse { emptyList() }

        val tokens = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        fun scoreItem(item: CinemanaItem): Int {
            val haystack = listOfNotNull(item.title, item.arTitle).joinToString(" ").lowercase()
            return tokens.count { haystack.contains(it) }
        }

        val combined = (moviesRaw + seriesRaw)
            .distinctBy { it.nb }
            .sortedByDescending { scoreItem(it) }
            .mapNotNull { it.toSearchResponse() }

        return newSearchResponseList(combined, combined.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfterLast("/")
        val detailsUrl = "$apiBase/allVideoInfo/id/$id"

        val item = parseCinemanaItem(app.get(detailsUrl).text) ?: return null

        val label = item.arTitle?.takeIf { it.isNotBlank() } ?: item.title ?: return null
        val plot = item.arContent?.takeIf { it.isNotBlank() } ?: item.enContent
        val tags = item.toTags()
        val actors = item.toActors()

        return if (item.kind == "2") {
            val seasonsUrl = "$apiBase/videoSeason/id/$id"
            val seasonsData = parseSeasonList(app.get(seasonsUrl).text).sortedBy { it.season }

            val episodes = seasonsData.flatMap { seasonItem ->
                val seasonNum = seasonItem.season
                (seasonItem.episodes ?: emptyList())
                    .mapNotNull { ep ->
                        val epNb = ep.nb?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        newEpisode(epNb) {
                            name = ep.title
                            season = seasonNum
                            episode = ep.episodeNummer
                            posterUrl = ep.imgObjUrl
                        }
                    }
                    .sortedBy { it.episode }
            }

            newTvSeriesLoadResponse(label, url, TvType.TvSeries, episodes) {
                this.plot = plot
                this.year = item.year
                this.tags = tags
                this.posterUrl = item.imgObjUrl
                this.actors = actors
            }
        } else {
            newMovieLoadResponse(label, url, TvType.Movie, id) {
                this.plot = plot
                this.year = item.year
                this.tags = tags
                this.posterUrl = item.imgObjUrl
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
        val linksUrl = "$mainUrl/transcoddedFiles/id/$data"
        val files = parseVideoFiles(app.get(linksUrl).text)
        if (files.isEmpty()) return false

        files.reversed().forEach { file ->
            val videoUrl = file.videoUrl
            val resolution = file.resolution?.takeIf { it.isNotBlank() } ?: file.fileFile ?: "Unknown"
            val quality = getQualityFromName(resolution.substringBefore("p"))

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
        return true
    }
}
