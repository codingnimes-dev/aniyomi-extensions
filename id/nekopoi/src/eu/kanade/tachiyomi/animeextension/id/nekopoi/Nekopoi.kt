package eu.kanade.tachiyomi.animeextension.id.nekopoi

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Nekopoi : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Nekopoi"
    override val baseUrl = "https://nekopoi.care"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    // =================== Popular Anime ===================

    override fun popularAnimeRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/category/hentai/", headers)
        } else {
            GET("$baseUrl/category/hentai/page/$page/", headers)
        }
    }

    override fun popularAnimeSelector() = "div.nk-hentai-grid ul li"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("a") ?: return anime

        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.selectFirst("div.title")?.text()?.trim()
            ?: link.attr("title").trim()

        val thumbDiv = element.selectFirst("div.nk-hentai-thumb")
        if (thumbDiv != null) {
            val style = thumbDiv.attr("style")
            val thumbUrl = style.substringAfter("url('").substringBefore("')")
            if (thumbUrl.isNotBlank()) {
                anime.thumbnail_url = thumbUrl
            }
        }

        return anime
    }

    override fun popularAnimeNextPageSelector() = "a.next.page-numbers"

    // =================== Latest Updates ===================

    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            GET(baseUrl, headers)
        } else {
            GET("$baseUrl/page/$page/", headers)
        }
    }

    override fun latestUpdatesSelector() = "div.nk-post-card"

    override fun latestUpdatesFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("a.nk-post-link") ?: element.selectFirst("a")
            ?: return anime

        // The episode link points to the episode page
        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.selectFirst("h2")?.text()?.trim()
            ?: link.selectFirst("span")?.text()?.trim()
            ?: link.text().trim()

        val thumbDiv = element.selectFirst("div.nk-thumb-crop")
        if (thumbDiv != null) {
            val style = thumbDiv.attr("style")
            val thumbUrl = style.substringAfter("url('").substringBefore("')")
            if (thumbUrl.isNotBlank()) {
                anime.thumbnail_url = thumbUrl
            }
        }

        return anime
    }

    override fun latestUpdatesNextPageSelector() = "a.next.page-numbers"

    // =================== Search ===================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/?s=$query", headers)
    }

    override fun searchAnimeSelector() = "div.nk-search-results ul li"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("a.nk-search-item") ?: return anime

        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.selectFirst("h2")?.text()?.trim() ?: link.text().trim()

        val thumbDiv = link.selectFirst("div.nk-search-thumb")
        if (thumbDiv != null) {
            val style = thumbDiv.attr("style")
            val thumbUrl = style.substringAfter("url('").substringBefore("')")
            if (thumbUrl.isNotBlank()) {
                anime.thumbnail_url = thumbUrl
            }
        }

        val genres = link.selectFirst("span.nk-search-genres")?.text()?.trim()
        val desc = link.selectFirst("p.nk-search-desc")?.text()?.trim()
        anime.description = listOfNotNull(genres, desc).joinToString("\n")

        return anime
    }

    override fun searchAnimeNextPageSelector(): String? = null

    // =================== Anime Details ===================

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        // Title from page title or first h1
        val titleEl = document.selectFirst("h1")
        val pageTitle = document.title()
        anime.title = titleEl?.text()?.trim()
            ?: pageTitle.substringAfter("Unduh \"").substringBefore("\"").trim()
            ?: pageTitle.trim()

        // Poster
        val poster = document.selectFirst("div.nk-series-poster")
        if (poster != null) {
            val style = poster.attr("style")
            val thumbUrl = style.substringAfter("url('").substringBefore("')")
            if (thumbUrl.isNotBlank()) {
                anime.thumbnail_url = thumbUrl
            }
        }

        // Meta info
        val metaList = document.select("div.nk-series-meta-list ul li")
        var description = ""
        for (item in metaList) {
            val label = item.selectFirst("b")?.text()?.trim() ?: continue
            val value = item.text().substringAfter(label).trim()
            when {
                label.contains("Judul Jepang", true) -> {
                    anime.title = anime.title.ifBlank { value.trim() }
                }
                label.contains("Status", true) -> {
                    anime.status = parseStatus(value.trim())
                }
                label.contains("Genre", true) -> {
                    description += "Genre: $value\n"
                }
                label.contains("Produser", true) -> {
                    description += "Produser: $value\n"
                }
            }
        }

        // Synopsis
        val synopsis = document.selectFirst("span.nk-series-synopsis")
        if (synopsis != null) {
            // Remove the bold title at the start
            synopsis.selectFirst("b")?.remove()
            val synopsisText = synopsis.text().trim()
            if (synopsisText.isNotBlank()) {
                description = synopsisText + "\n\n" + description
            }
        }

        anime.description = description.trim()
        anime.artist = "Nekopoi"

        return anime
    }

    private fun parseStatus(status: String): Int {
        return when {
            status.contains("Ongoing", true) || status.contains("Ongoing", true) -> SAnime.ONGOING
            status.contains("Completed", true) || status.contains("Completed", true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }

    // =================== Episode List ===================

    override fun episodeListSelector() = "div.nk-episode-grid a.nk-episode-card"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val elements = document.select(episodeListSelector())
        val episodes = mutableListOf<SEpisode>()

        // Also check for latest episode quick links
        val latestEpisodes = document.select("div.nk-latest-episode div.latestnow a")

        // Merge: use the episode grid for all episodes
        for (element in elements) {
            val episode = episodeFromElement(element)
            if (episode.url.isNotBlank()) {
                episodes.add(episode)
            }
        }

        return episodes.reversed() // Chronological order (oldest first)
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))

        val badge = element.selectFirst("span.nk-episode-badge")
        val title = element.selectFirst("span.nk-episode-card-title")

        val badgeText = badge?.text()?.trim() ?: ""
        val titleText = title?.text()?.trim() ?: ""

        // Parse episode number from badge (e.g., "Ep 1" -> 1)
        val epNum = badgeText.replace("Ep", "", true).trim().toFloatOrNull()
        episode.episode_number = epNum ?: 1f

        episode.name = if (titleText.isNotBlank()) titleText else badgeText

        // Date
        val dateEl = element.selectFirst("span.nk-episode-card-date")
        if (dateEl != null) {
            val dateText = dateEl.text().trim()
            try {
                val sdf = SimpleDateFormat("d MMMM yyyy", Locale("id"))
                episode.date_upload = sdf.parse(dateText)?.time ?: 0L
            } catch (_: Exception) {
                episode.date_upload = 0L
            }
        }

        return episode
    }

    // =================== Video List ===================

    override fun videoListSelector() = "div.nk-player-frame"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        // Parse iframes from player tabs
        val playerFrames = document.select("div.nk-player-frame iframe")
        var qualityIdx = 0
        val qualities = listOf("360P", "480P", "720P", "Server")

        for (iframe in playerFrames) {
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                val quality = if (qualityIdx < qualities.size) qualities[qualityIdx] else "Server ${qualityIdx + 1}"
                videos.add(Video(src, quality, src))
                qualityIdx++
            }
        }

        // If no iframes found in the standard structure, try direct player divs
        if (videos.isEmpty()) {
            val altIframes = document.select("div#nk-player iframe, iframe[src*=playmogo], iframe[src*=streampoi]")
            for (iframe in altIframes) {
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    videos.add(Video(src, "Server ${videos.size + 1}", src))
                }
            }
        }

        return videos
    }

    override fun videoFromElement(element: Element): Video {
        val iframe = element.selectFirst("iframe")
        val src = iframe?.attr("src") ?: ""
        return Video(src, "Video", src)
    }

    override fun videoUrlParse(document: Document): String {
        // Return the first iframe URL found
        val iframe = document.selectFirst("div.nk-player-frame iframe, iframe[src*=playmogo], iframe[src*=streampoi]")
        return iframe?.attr("src") ?: ""
    }

    override fun videoListRequest(episode: SEpisode): Request {
        return GET("$baseUrl${episode.url}", headers)
    }

    // =================== Preferences ===================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = "preferred_server"
            title = "Preferred Server"
            summary = "%s"
            entries = arrayOf("Server 1 (Auto)", "Server 2 (360P/480P)", "Server 3 (720P)")
            entryValues = arrayOf("0", "1", "2")
            setDefaultValue("0")
        }
        screen.addPreference(serverPref)
    }

    // =================== Helpers ===================

    companion object {
        private const val TAG = "Nekopoi"
    }
}
