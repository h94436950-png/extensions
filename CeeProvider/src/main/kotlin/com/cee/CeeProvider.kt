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

    // ── Main page sections ────────────────────────────────────────────────────
    // request.data encodes: "<paginatedUrl>||PAGE_BASE::<baseTemplate>"
    // getMainPage appends the page number to the base template.
    // For simplicity the templates below end with pageNumber= so we can
    // do: baseTemplate + page.
    override val mainPage = mainPageOf(
        "$mainUrl/newlyVideosItems/level/0/offset/12/page/"
                to "جديد",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&level=0&sortParam=views_desc&pageNumber="
                to "أفلام - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&level=0&sortParam=views_desc&pageNumber="
                to "مسلسلات - الأكثر مشاهدة",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&level=0&sortParam=stars_desc&pageNumber="
                to "أفلام - الأعلى تقييماً",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&level=0&sortParam=stars_desc&pageNumber="
                to "مسلسلات - الأعلى تقييماً",
        "$apiBase/video/V/2?videoKind=1&langNb=&itemsPerPage=30&level=0&sortParam=asc&pageNumber="
                to "أفلام - الأحدث",
        "$apiBase/video/V/2?videoKind=2&langNb=&itemsPerPage=30&level=0&sortParam=asc&pageNumber="
                to "مسلسلات - الأحدث",
    )

    // ── Data classes ──────────────────────────────────────────────────────────

    /**
     * Core video item returned by list, search and detail APIs.
     * Constructor signature (12 fields):
     *   String×6 → Integer → String×3 → List×2
     */
    data class CinemanaItem(
        @JsonProperty("nb")          val nb:          String?,
        @JsonProperty("title")       val title:       String?,
        @JsonProperty("ar_title")    val arTitle:     String?,
        @JsonProperty("en_content")  val enContent:   String?,
        @JsonProperty("ar_content")  val arContent:   String?,
        @JsonProperty("stars")       val stars:       String?,
        @JsonProperty("year")        val year:        Int?,
        @JsonProperty("kind")        val kind:        String?,   // "1"=Movie, "2"=Series
        @JsonProperty("imgObjUrl")   val imgObjUrl:   String?,
        @JsonProperty("linkName")    val linkName:    String?,
        @JsonProperty("actorsInfo")  val actorsInfo:  List<ActorInfo>?,
        @JsonProperty("categories")  val categories:  List<Category>?
    )

    data class ActorInfo(
        @JsonProperty("nb")                    val nb:                  String?,
        @JsonProperty("title")                 val title:               String?,
        @JsonProperty("role")                  val role:                String?,
        @JsonProperty("staff_img")             val staffImg:            String?,
        @JsonProperty("staff_img_medium_thumb") val staffImgMediumThumb: String?,
        @JsonProperty("staff_img_thumb")       val staffImgThumb:       String?
    )

    data class Category(
        @JsonProperty("en_title") val enTitle: String?,
        @JsonProperty("ar_title") val arTitle: String?
    )

    data class VideoListResponse(
        @JsonProperty("items")   val items:   List<CinemanaItem>?,
        @JsonProperty("hasMore") val hasMore: Boolean?
    )

    /** One season with its episodes, from /videoSeason/id/{id} */
    data class SeasonNumberItem(
        @JsonProperty("season")   val season:   Int?,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>?
    )

    data class EpisodeItem(
        @JsonProperty("nb")            val nb:            String?,
        @JsonProperty("title")         val title:         String?,
        @JsonProperty("episodeNummer") val episodeNummer: Int?,
        @JsonProperty("imgObjUrl")     val imgObjUrl:     String?
    )

    /** One transcoded video file from /transcoddedFiles/id/{id} */
    data class VideoFile(
        @JsonProperty("videoUrl")   val videoUrl:  String?,
        @JsonProperty("resolution") val resolution: String?,
        @JsonProperty("fileFile")   val fileFile:   String?
    )

    data class VideoGroup(
        @JsonProperty("id")       val id:      String?,
        @JsonProperty("en_title") val enTitle: String?,
        @JsonProperty("ar_title") val arTitle: String?
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun CinemanaItem.toSearchResponse(): SearchResponse? {
        val id    = nb?.takeIf { it.isNotBlank() } ?: return null
        val label = arTitle?.takeIf { it.isNotBlank() } ?: title ?: return null
        val type  = if (kind == "2") TvType.TvSeries else TvType.Movie
        val url   = "$mainUrl/details/$id"

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(label, url, type) {
                posterUrl      = imgObjUrl
                this.year      = this@toSearchResponse.year
            }
        } else {
            newMovieSearchResponse(label, url, type) {
                posterUrl      = imgObjUrl
                this.year      = this@toSearchResponse.year
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
        categories?.mapNotNull { cat ->
            (cat.arTitle?.takeIf { it.isNotBlank() } ?: cat.enTitle)
        }?.takeIf { it.isNotEmpty() }

    // ── MainPage ──────────────────────────────────────────────────────────────

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // request.data ends with "pageNumber=" or "page/" — append page directly
        val url = "${request.data}$page"
        return try {
            val resp   = app.get(url).text
            val parsed = tryParseJson<VideoListResponse>(resp)
            val items  = parsed?.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()
            newHomePageResponse(
                listOf(HomePageList(request.name, items, isHorizontalImages = true)),
                hasNext = parsed?.hasMore ?: false
            )
        } catch (e: Exception) {
            logError(e)
            newHomePageResponse(emptyList(), false)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val enc = query.encodeUri()

        // Calculated API page parameter (0-indexed)
        val pageParam = page - 1

        val moviesUrl =
            "$mainUrl/AdvancedSearch?level=0&videoTitle=$enc&staffTitle=$enc" +
                    "&type=movies&itemsPerPage=$itemsPerPageSearch&pageNumber=$pageParam&year="
        val seriesUrl =
            "$mainUrl/AdvancedSearch?level=0&videoTitle=$enc&staffTitle=$enc" +
                    "&type=series&itemsPerPage=$itemsPerPageSearch&pageNumber=$pageParam&year="

        val moviesRaw = runCatching {
            tryParseJson<VideoListResponse>(app.get(moviesUrl).text)?.items ?: emptyList()
        }.getOrElse { emptyList() }

        val seriesRaw = runCatching {
            tryParseJson<VideoListResponse>(app.get(seriesUrl).text)?.items ?: emptyList()
        }.getOrElse { emptyList() }

        // Interleave movies + series, score by title relevance
        val tokens = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        fun scoreItem(item: CinemanaItem): Int {
            val haystack = listOfNotNull(item.title, item.arTitle)
                .joinToString(" ").lowercase()
            val tokenMatches = tokens.count { haystack.contains(it) }
            return tokenMatches
        }

        val combined = (moviesRaw + seriesRaw)
            .distinctBy { it.nb }
            .sortedByDescending { scoreItem(it) }
            .mapNotNull { it.toSearchResponse() }

        return combined.toNewSearchResponseList()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfterLast("/")
        val detailsUrl = "$apiBase/allVideoInfo/id/$id"

        val item = tryParseJson<CinemanaItem>(app.get(detailsUrl).text) ?: return null

        val label   = item.arTitle?.takeIf { it.isNotBlank() } ?: item.title ?: return null
        val plot    = item.arContent?.takeIf { it.isNotBlank() } ?: item.enContent
        val tags    = item.toTags()
        val actors  = item.toActors()
        val rating  = item.stars?.toFloatOrNull()?.let { (it * 100).toInt() }

        return if (item.kind == "2") {
            // ── TV Series ──────────────────────────────────────────────────
            val seasonsUrl = "$apiBase/videoSeason/id/$id"
            val seasonsData: List<SeasonNumberItem> = runCatching {
                tryParseJson<List<SeasonNumberItem>>(app.get(seasonsUrl).text) ?: emptyList()
            }.getOrElse { emptyList() }

            val sortedSeasonNumbers = seasonsData.sortedBy { it.season }

            val episodes = sortedSeasonNumbers.flatMap { seasonItem ->
                val seasonNum = seasonItem.season
                (seasonItem.episodes ?: emptyList()).mapNotNull { ep ->
                    val epNb = ep.nb?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    newEpisode(epNb) {
                        name     = ep.title
                        season   = seasonNum
                        episode  = ep.episodeNummer
                        posterUrl = ep.imgObjUrl
                    }
                }.sortedBy { it.episode }
            }

            newTvSeriesLoadResponse(label, url, TvType.TvSeries, episodes) {
                this.plot      = plot
                this.year      = item.year
                this.tags      = tags
                this.posterUrl = item.imgObjUrl
                this.actors    = actors
                this.rating    = rating
            }
        } else {
            // ── Movie ──────────────────────────────────────────────────────
            newMovieLoadResponse(label, url, TvType.Movie, id) {
                this.plot      = plot
                this.year      = item.year
                this.tags      = tags
                this.posterUrl = item.imgObjUrl
                this.actors    = actors
                this.rating    = rating
            }
        }
    }

    // ── LoadLinks ─────────────────────────────────────────────────────────────

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linksUrl = "$mainUrl/transcoddedFiles/id/$data"
        val files = tryParseJson<List<VideoFile>>(app.get(linksUrl).text)
            ?: return false

        // Reverse to show highest quality first (6 links typical)
        files.reversed().forEach { file ->
            val videoUrl   = file.videoUrl ?: return@forEach
            val resolution = file.resolution?.takeIf { it.isNotBlank() } ?: file.fileFile ?: "Unknown"
            val quality    = getQualityFromName(resolution.substringBefore("p"))
            callback(
                newExtractorLink(
                    source  = this.name,
                    name    = "${this.name} ($resolution)",
                    url     = videoUrl
                ) {
                    this.quality = quality
                    this.referer = mainUrl
                }
            )
        }
        return true
    }
}
