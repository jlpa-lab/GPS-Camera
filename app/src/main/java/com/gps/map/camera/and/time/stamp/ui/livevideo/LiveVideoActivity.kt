package com.gps.map.camera.and.time.stamp.ui.livevideo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.BuildConfig
import com.gps.map.camera.and.time.stamp.MainActivity
import com.gps.map.camera.and.time.stamp.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Browseable live camera catalogue. Each preview opens the existing YouTube player. */
class LiveVideoActivity : AppCompatActivity() {
    private enum class VideoTab { POPULAR, NATURE, CITIES }

    companion object {
        private const val RECENT_SEARCHES_KEY = "live_video_recent_searches"
        private const val RECENT_SEARCHES_SEPARATOR = "\u001F"
    }

    private data class LiveVideoPin(
        val id: Int,
        val name: String,
        val youtubeId: String,
        val location: String? = null,
        val thumbnailUrl: String? = null
    )

    private lateinit var searchInput: EditText
    private lateinit var videoList: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var resultsHeading: TextView
    private lateinit var loading: View
    private lateinit var adapter: LiveVideoAdapter
    private lateinit var recentSearches: View
    private lateinit var recentSearchList: LinearLayout
    private lateinit var noRecentSearches: TextView

    private var allVideos: List<LiveVideoPin> = emptyList()
    private var selectedTab: VideoTab? = VideoTab.POPULAR
    private var searchQuery = ""
    private var dataLoadFailed = false
    private var remoteSearchPending = false
    private var remoteSearchFailed = false
    private var remoteSearchRequest = 0
    private val remoteResults = mutableMapOf<String, List<LiveVideoPin>>()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private val tabIds = setOf(
        R.id.live_video_tab_popular,
        R.id.live_video_tab_nature,
        R.id.live_video_tab_cities
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_video)

        searchInput = findViewById(R.id.live_video_search)
        videoList = findViewById(R.id.live_video_list)
        emptyState = findViewById(R.id.live_video_empty_state)
        resultsHeading = findViewById(R.id.live_video_results_heading)
        loading = findViewById(R.id.live_video_loading)
        recentSearches = findViewById(R.id.live_video_recent_searches)
        recentSearchList = findViewById(R.id.live_video_recent_search_list)
        noRecentSearches = findViewById(R.id.live_video_no_recent_searches)
        adapter = LiveVideoAdapter(::openVideo)

        videoList.adapter = adapter
        videoList.setHasFixedSize(true)
        findViewById<View>(R.id.live_video_back).setOnClickListener { returnToHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToHome()
        })

        val tabs = mapOf(
            R.id.live_video_tab_popular to VideoTab.POPULAR,
            R.id.live_video_tab_nature to VideoTab.NATURE,
            R.id.live_video_tab_cities to VideoTab.CITIES
        )
        tabs.forEach { (viewId, tab) ->
            findViewById<TextView>(viewId).setOnClickListener {
                searchInput.clearFocus()
                selectedTab = tab
                updateTabSelection()
                updateSearchMode()
                renderVideos()
            }
        }
        updateTabSelection()

        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && searchQuery.isNotBlank()) saveRecentSearch(searchQuery)
            if (!hasFocus && searchQuery.isBlank()) {
                selectedTab = VideoTab.POPULAR
                updateTabSelection()
            }
            updateSearchMode()
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != 0 && searchQuery.isNotBlank()) {
                saveRecentSearch(searchQuery)
                true
            } else {
                false
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                if (searchQuery.isNotBlank()) {
                    selectedTab = null
                    updateTabSelection()
                }
                updateSearchMode()
                renderVideos()
                scheduleYoutubeSearch()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        loadVideos()
    }

    private fun returnToHome() {
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        finish()
    }

    private fun updateTabSelection() {
        val selectedId = when (selectedTab) {
            VideoTab.POPULAR -> R.id.live_video_tab_popular
            VideoTab.NATURE -> R.id.live_video_tab_nature
            VideoTab.CITIES -> R.id.live_video_tab_cities
            null -> null
        }
        tabIds.forEach { id -> findViewById<TextView>(id).isSelected = id == selectedId }
    }

    private fun updateSearchMode() {
        val isSearchMode = searchInput.hasFocus()
        val isRecentSearchState = isSearchMode && searchQuery.isBlank()
        recentSearches.visibility = if (isRecentSearchState) View.VISIBLE else View.GONE
        resultsHeading.visibility = if (searchQuery.isNotBlank()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.live_video_tabs).visibility = if (isSearchMode) View.GONE else View.VISIBLE
        if (isRecentSearchState) {
            videoList.visibility = View.GONE
            emptyState.visibility = View.GONE
            resultsHeading.visibility = View.GONE
            renderRecentSearches()
        } else {
            videoList.visibility = View.VISIBLE
        }
    }

    private fun renderRecentSearches() {
        recentSearchList.removeAllViews()
        val searches = recentSearchValues()
        noRecentSearches.visibility = if (searches.isEmpty()) View.VISIBLE else View.GONE
        searches.forEach { query ->
            val row = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
                setPadding(dp(8), 0, dp(8), 0)
                typeface = resources.getFont(R.font.poppins_semibold)
                setTextColor(getColor(R.color.primary_text_color))
                textSize = 16f
                text = query
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    searchInput.setText(query)
                    searchInput.setSelection(searchInput.text.length)
                }
            }
            recentSearchList.addView(row)
        }
    }

    private fun scheduleYoutubeSearch() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        val query = searchQuery.trim()
        if (query.length < 2 || BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            remoteSearchPending = false
            remoteSearchFailed = false
            return
        }
        remoteSearchPending = true
        renderVideos()
        val request = Runnable { searchYoutubeLive(query) }
        pendingSearch = request
        searchHandler.postDelayed(request, 450L)
    }

    private fun searchYoutubeLive(query: String) {
        val requestId = ++remoteSearchRequest
        Thread {
            val result = runCatching { fetchYoutubeLiveResults(query) }
            runOnUiThread {
                if (requestId != remoteSearchRequest || !searchQuery.equals(query, ignoreCase = true)) return@runOnUiThread
                remoteSearchPending = false
                remoteSearchFailed = result.isFailure
                val key = query.lowercase(Locale.getDefault())
                if (result.isFailure) {
                    remoteResults.remove(key)
                } else {
                    remoteResults[key] = result.getOrDefault(emptyList())
                }
                renderVideos()
            }
        }.start()
    }

    private fun fetchYoutubeLiveResults(query: String): List<LiveVideoPin> {
        val searchTerm = if (query.contains("webcam", ignoreCase = true) || query.contains("camera", ignoreCase = true)) {
            query
        } else {
            "$query live webcam"
        }
        val endpoint = Uri.Builder()
            .scheme("https")
            .authority("www.googleapis.com")
            .path("youtube/v3/search")
            .appendQueryParameter("part", "snippet")
            .appendQueryParameter("q", searchTerm)
            .appendQueryParameter("type", "video")
            .appendQueryParameter("eventType", "live")
            .appendQueryParameter("videoEmbeddable", "true")
            .appendQueryParameter("videoSyndicated", "true")
            .appendQueryParameter("order", "viewCount")
            .appendQueryParameter("maxResults", "25")
            .appendQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
            .build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("YouTube live search failed with HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            val displayLocation = query.toDisplayLocation()
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    val snippet = item.optJSONObject("snippet") ?: continue
                    if (videoId.isBlank()) continue
                    val title = snippet.optString("title")
                    val searchableMetadata = listOf(
                        title,
                        snippet.optString("description"),
                        snippet.optString("channelTitle")
                    ).joinToString(" ")
                    if (!isRelevantCameraResult(query, searchableMetadata)) continue
                    add(
                        LiveVideoPin(
                            id = -index - 1,
                            name = title.ifBlank { "$displayLocation Live Camera" },
                            youtubeId = videoId,
                            location = displayLocation,
                            thumbnailUrl = snippet.optJSONObject("thumbnails")
                                ?.optJSONObject("high")?.optString("url")
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun isRelevantCameraResult(query: String, metadata: String): Boolean {
        val searchable = normalizeLocationText(metadata)
        if (!locationMatchesQuery(query, searchable)) return false
        val blockedTerms = listOf(
            "breaking news", "live news", "news live", "gaming", "gameplay", "grand theft auto",
            "gta", "music", "song", "concert", "dj set", "playlist", "podcast", "radio",
            "planet earth", "earth 4k", "earth 8k", "4k live earth", "earth live", "live earth",
            "outer space", "space live", "around the world",
            "rolling cam with chill"
        )
        return blockedTerms.none(searchable::contains)
    }

    private fun locationMatchesQuery(query: String, searchable: String): Boolean {
        val normalized = normalizeLocationText(query)
            .replace(Regex("\\b(live|camera|webcam|stream|view|weather)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return false
        val aliases = when {
            normalized == "india" -> setOf(
                "india", "indian", "mumbai", "new delhi", "delhi", "bengaluru", "bangalore",
                "hyderabad", "chennai", "kolkata", "goa", "jaipur", "agra", "varanasi"
            )
            normalized == "las vegas" -> setOf("las vegas", "vegas", "nevada")
            normalized == "new york" -> setOf("new york", "new york city", "manhattan", "nyc")
            normalized == "united states" || normalized == "usa" -> setOf("usa", "united states", "america")
            normalized == "united kingdom" || normalized == "uk" -> setOf("united kingdom", "uk", "england", "london")
            else -> emptySet()
        }
        if (aliases.isNotEmpty()) return aliases.any(searchable::contains)
        val terms = normalized.split(" ").filter { it.length > 2 }
        return terms.isNotEmpty() && terms.all(searchable::contains)
    }

    private fun normalizeLocationText(value: String): String = value
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun recentSearchValues(): List<String> = getPreferences(MODE_PRIVATE)
        .getString(RECENT_SEARCHES_KEY, null)
        ?.split(RECENT_SEARCHES_SEPARATOR)
        ?.filter { it.isNotBlank() }
        .orEmpty()

    private fun saveRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val values = (listOf(normalized) + recentSearchValues()
            .filterNot { it.equals(normalized, ignoreCase = true) })
            .take(5)
        getPreferences(MODE_PRIVATE).edit()
            .putString(RECENT_SEARCHES_KEY, values.joinToString(RECENT_SEARCHES_SEPARATOR))
            .apply()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        remoteSearchRequest++
        super.onDestroy()
    }

    private fun loadVideos() {
        Thread {
            val result = runCatching {
                val jsonText = assets.open("live_data_video.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonText)
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(index) ?: continue
                        val name = item.optString("name").trim()
                        val youtubeId = normalizeYoutubeId(item.optString("linkVideo"))
                        if (name.isNotEmpty() && youtubeId != null) {
                            add(LiveVideoPin(item.optInt("id"), name, youtubeId))
                        }
                    }
                }
            }

            runOnUiThread {
                allVideos = result.getOrDefault(emptyList())
                dataLoadFailed = result.isFailure || allVideos.isEmpty()
                loading.visibility = View.GONE
                renderVideos()
            }
        }.start()
    }

    private fun renderVideos() {
        if (searchInput.hasFocus() && searchQuery.isBlank()) {
            adapter.submitList(emptyList())
            emptyState.visibility = View.GONE
            return
        }
        val filtered = filteredVideos()
        adapter.submitList(filtered)
        loading.visibility = if (remoteSearchPending) View.VISIBLE else View.GONE
        resultsHeading.visibility = if (searchQuery.isNotBlank()) View.VISIBLE else View.GONE
        if (searchQuery.isNotBlank()) {
            resultsHeading.text = getString(
                R.string.live_video_search_results_heading,
                filtered.size,
                searchQuery.toDisplayLocation()
            )
        }
        emptyState.visibility = if (filtered.isEmpty() && !remoteSearchPending) View.VISIBLE else View.GONE
        if (dataLoadFailed) {
            emptyState.setText(R.string.live_video_data_unavailable)
        } else if (remoteSearchFailed) {
            emptyState.setText(R.string.live_video_search_unavailable)
        } else if (searchQuery.isNotBlank()) {
            emptyState.text = getString(R.string.live_video_no_results_for_query, searchQuery)
        } else {
            emptyState.setText(R.string.live_video_no_results)
        }
    }

    private fun filteredVideos(): List<LiveVideoPin> {
        val query = searchQuery.lowercase(Locale.getDefault())
        val searchPool = if (query.isBlank()) {
            allVideos
        } else {
            (allVideos + remoteResults[query].orEmpty()).distinctBy { it.youtubeId }
        }
        val matches = searchPool.filter { video ->
            val searchableText = searchableText(video)
            val matchesSearch = query.isBlank() || searchableText.contains(query)
            val matchesTab = when (selectedTab) {
                null -> true
                VideoTab.POPULAR -> isPopular(video)
                VideoTab.NATURE -> isNature(video)
                VideoTab.CITIES -> isCity(video)
            }
            matchesSearch && matchesTab
        }
        return if (selectedTab == VideoTab.POPULAR) {
            matches.sortedBy { popularRank(it) }
        } else {
            matches
        }
    }

    private fun searchableText(video: LiveVideoPin): String =
        "${video.name} ${locationLabel(video)}".lowercase(Locale.getDefault())

    private fun popularRank(video: LiveVideoPin): Int {
        val value = video.name.lowercase(Locale.getDefault())
        return when {
            "new york" in value -> 0
            "tokyo shinjuku" in value -> 1
            "chicago skyline" in value -> 2
            "canadian" in value -> 3
            else -> 10 + allVideos.indexOf(video)
        }
    }

    private fun isPopular(video: LiveVideoPin): Boolean {
        val value = video.name.lowercase(Locale.getDefault())
        return listOf(
            "new york", "tokyo", "chicago", "sydney", "london", "dubai",
            "singapore", "venice", "jerusalem", "honolulu", "prague", "athens"
        ).any(value::contains) || allVideos.indexOf(video) < 12
    }

    private fun isNature(video: LiveVideoPin): Boolean {
        val value = video.name.lowercase(Locale.getDefault())
        return listOf(
            "beach", "mountain", "rockies", "lake", "river", "falls", "desert",
            "volcano", "ocean", "island", "ski", "surf", "forest", "park", "coast",
            "bay", "marina", "resort", "geiranger", "khumbu", "zermatt", "nature"
        ).any(value::contains)
    }

    private fun isCity(video: LiveVideoPin): Boolean {
        val value = video.name.lowercase(Locale.getDefault())
        return listOf(
            "city", "square", "street", "bridge", "airport", "downtown", "tokyo",
            "new york", "chicago", "london", "sydney", "prague", "dubai", "singapore",
            "venice", "athens", "jerusalem", "belgrade", "shinjuku", "honolulu"
        ).any(value::contains)
    }

    private fun openVideo(video: LiveVideoPin) {
        startActivity(Intent(this, YoutubeVideoActivity::class.java).apply {
            putExtra(YoutubeVideoActivity.EXTRA_VIDEO_ID, video.youtubeId)
            putExtra(YoutubeVideoActivity.EXTRA_VIDEO_TITLE, displayTitle(video))
            putExtra(YoutubeVideoActivity.EXTRA_LOCATION, locationLabel(video))
            putExtra(YoutubeVideoActivity.EXTRA_PIN_ID, video.id)
        })
    }

    private fun displayTitle(video: LiveVideoPin): String {
        val value = video.name.lowercase(Locale.getDefault())
        return when {
            video.id == 650 -> "Manhattan View"
            "new york" in value -> "New York Skyline"
            "chicago skyline" in value -> "Chicago Skyline"
            "tokyo shinjuku" in value -> "Tokyo Shinjuku"
            else -> video.name
        }
    }

    private fun String.toDisplayLocation(): String = trim().split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }

    private fun locationLabel(video: LiveVideoPin): String {
        video.location?.takeIf { it.isNotBlank() }?.let { return it }
        val value = video.name.lowercase(Locale.getDefault())
        return when {
            "new york" in value -> "New York, USA"
            "chicago" in value -> "Chicago, USA"
            "tokyo" in value -> "Tokyo, Japan"
            "sydney" in value -> "Sydney, Australia"
            "dubai" in value -> "Dubai, UAE"
            video.name.contains(",") -> video.name.substringAfterLast(",").trim()
            else -> "Worldwide"
        }
    }

    private fun normalizeYoutubeId(value: String): String? {
        val trimmed = value.trim()
        val id = when {
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore('?').substringBefore('/')
            trimmed.contains("youtube.com/watch") -> Uri.parse(trimmed).getQueryParameter("v")
            trimmed.contains("youtube.com/embed/") -> trimmed.substringAfter("/embed/").substringBefore('?').substringBefore('/')
            else -> trimmed.takeIf { !it.contains("/") && !it.contains(".") }
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
    }

    private class LiveVideoAdapter(
        private val onVideoClick: (LiveVideoPin) -> Unit
    ) : RecyclerView.Adapter<LiveVideoAdapter.VideoViewHolder>() {
        private var items: List<LiveVideoPin> = emptyList()

        fun submitList(newItems: List<LiveVideoPin>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.row_live_video_card, parent, false)
            return VideoViewHolder(view)
        }

        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            holder.bind(items[position], onVideoClick)
        }

        override fun getItemCount(): Int = items.size

        class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val thumbnail: ImageView = itemView.findViewById(R.id.live_video_thumbnail)
            private val title: TextView = itemView.findViewById(R.id.live_video_card_title)
            private val location: TextView = itemView.findViewById(R.id.live_video_card_location)

            fun bind(video: LiveVideoPin, onVideoClick: (LiveVideoPin) -> Unit) {
                title.text = when {
                    video.id == 650 -> "Manhattan View"
                    video.name.contains("new york", ignoreCase = true) -> "New York Skyline"
                    video.name.contains("chicago skyline", ignoreCase = true) -> "Chicago Skyline"
                    video.name.contains("tokyo shinjuku", ignoreCase = true) -> "Tokyo Shinjuku"
                    else -> video.name
                }
                location.text = "LIVE • ${video.location ?: when {
                    video.name.contains("new york", ignoreCase = true) -> "New York, USA"
                    video.name.contains("chicago", ignoreCase = true) -> "Chicago, USA"
                    video.name.contains("tokyo", ignoreCase = true) -> "Tokyo, Japan"
                    video.name.contains("sydney", ignoreCase = true) -> "Sydney, Australia"
                    video.name.contains("dubai", ignoreCase = true) -> "Dubai, UAE"
                    video.name.contains(",") -> video.name.substringAfterLast(",").trim()
                    else -> "Worldwide"
                }}"
                Glide.with(thumbnail)
                    .load(video.thumbnailUrl ?: "https://i.ytimg.com/vi/${video.youtubeId}/hqdefault.jpg")
                    .placeholder(R.drawable.bg_live_video_black)
                    .error(R.drawable.bg_live_video_black)
                    .centerCrop()
                    .into(thumbnail)
                itemView.setOnClickListener { onVideoClick(video) }
            }
        }
    }
}
