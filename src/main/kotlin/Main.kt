import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.PlaylistItemSnippet
import com.google.api.services.youtube.model.ResourceId
import io.github.pr0methean.betterrandom.prng.concurrent.SingleThreadSplittableRandomAdapter
import java.nio.file.Paths

/**
 * The maximum number of results per page when querying playlist contents. Set by YouTube.
 */
private const val MAX_RESULTS = 50.toLong()

/**
 * Needed for the YouTube API.
 */
val JSON_FACTORY: JacksonFactory = JacksonFactory.getDefaultInstance()

/**
 * Access permissions. Rewriting a playlist requires that these be broad.
 */
private val SCOPES = listOf("https://www.googleapis.com/auth/youtube.force-ssl")

/**
 * <p>
 * Rewrites a YouTube playlist by reading from the text files in a directory
 * specified as the first argument.
 * </p><p>
 * Each list is shuffled, and the sub-lists are rotated so that they are mixed
 * as proportionately as possible, and no sub-list is drawn from twice in a row
 * unless necessary.
 * </p><p>
 * The constants DEVELOPER_KEY, APPLICATION_NAME, CLIENT_ID, CLIENT_SECRET and
 * PLAYLIST_ID are stored in unpublished Kotlin files.
 * FIXME: Switch to a standard configuration format.
 * </p>
 */
fun main(args: Array<String>) {
    // Create shuffled mix
    val random = SingleThreadSplittableRandomAdapter()
    val folder = Paths.get(
        if (args.isEmpty()) {
            ""
        } else {
            args[0]
        }
    ).toAbsolutePath().toFile()
    println("Working in $folder")
    val listFiles = folder.listFiles { file ->
        file.isFile
    } ?: error("Argument must be a directory")
    println("Found files: ${listFiles.joinToString { file -> file.toString() }}")
    val lists = mutableListOf<List<String>>()
    var totalEntries = 0
    for (file in listFiles) {
        val lines = file.readLines(Charsets.UTF_8)
            .map { line -> line.split("#")[0].trim() }
            .filter { line -> line.isNotEmpty() }
            .shuffled(random)
        if (lines.isNotEmpty()) {
            lists.add(lines)
        }
        totalEntries += lines.size
    }
    lists.shuffle(random) // so that sort below (which is stable) gets random tiebreaking
    lists.sortByDescending { it.size } // draw from longest list first
    val listPositions = MutableList(lists.size) { 0 }
    var output = 0
    var lastChoice = -1
    val merged = mutableListOf<String>()
    while (output < totalEntries) {
        var bestIndex = -1
        var lowestFractionDone = 1.0
        for (i in lists.indices) {
            if (i == lastChoice) {
                continue // avoid drawing from same list twice in a row
            }
            val fractionDone = listPositions[i].toDouble() / lists[i].size
            if (fractionDone < lowestFractionDone) {
                lowestFractionDone = fractionDone
                bestIndex = i
            }
        }
        if (bestIndex == -1) {
            bestIndex = lastChoice // draw from same list twice only as a last resort
        }
        val nextVideo = lists[bestIndex][listPositions[bestIndex]]
        listPositions[bestIndex]++
        merged.add(nextVideo)
        println(nextVideo)
        output++
        lastChoice = bestIndex
    }
    // Build flow and trigger user authorization request.
    val clientSecrets = GoogleClientSecrets()
    val details = GoogleClientSecrets.Details()
    details.clientId = CLIENT_ID
    details.clientSecret = CLIENT_SECRET
    clientSecrets.installed = details
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    // Build flow and trigger user authorization request.
    val flow = GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
        .build()
    val credential = AuthorizationCodeInstalledApp(flow, LocalServerReceiver()).authorize("user")
    val service = YouTube.Builder(httpTransport, JSON_FACTORY, credential)
        .setApplicationName(APPLICATION_NAME)
        .build()
    println("Deleting old contents....")
    val playlistItems = service.playlistItems()

    // Delete old contents
    val listRequest = playlistItems.list("id")
    listRequest.playlistId = PLAYLIST_ID
    listRequest.maxResults = MAX_RESULTS
    // For some reason, YouTube starts at the end and returns a prevPageToken rather
    // than a nextPageToken.
    var prevPageToken: String? = null
    do {
        listRequest.pageToken = prevPageToken
        val listResponse = listRequest.setKey(DEVELOPER_KEY).execute()
        val items = listResponse.items
        prevPageToken = listResponse.prevPageToken
        if (items.isEmpty() && prevPageToken.isNullOrEmpty()) {
            println("Warning: detected that the playlist is currently empty!")
            break
        }
        // FIXME: Check for a prefix of the new playlist that's a subsequence of the old;
        // then it doesn't have to be deleted and re-added and will save quota.
        for (video in items) {
            playlistItems.delete(video.id).setKey(DEVELOPER_KEY).execute()
        }
    } while (prevPageToken != null)
    println("Adding new contents....")
    // Add new contents
    for (video in merged) {
        val playlistItem = PlaylistItem()

        // Add the snippet object property to the PlaylistItem object.
        val snippet = PlaylistItemSnippet()
        snippet.playlistId = PLAYLIST_ID
        snippet.position = 0L
        val resourceId = ResourceId()
        resourceId.kind = "youtube#video"
        resourceId.videoId = video
        snippet.resourceId = resourceId
        playlistItem.snippet = snippet

        // Add the item to the playlist
        playlistItems.insert("snippet", playlistItem).setKey(DEVELOPER_KEY).execute()
    }
    println("Finished!")
}