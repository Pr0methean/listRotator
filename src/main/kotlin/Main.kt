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


const val MAX_RESULTS = 50.toLong() // max allowed by YouTube
// val DEVICE_LOGIN_ENDPOINT = URI("https://accounts.google.com/o/oauth2/device/code")
// val TOKEN_ENDPOINT = URI("https://oauth2.googleapis.com/token")
val JSON_FACTORY: JacksonFactory = JacksonFactory.getDefaultInstance()
// val TO_INPUT_STREAM = HttpResponse.BodyHandlers.ofInputStream()
private val SCOPES = listOf("https://www.googleapis.com/auth/youtube.force-ssl")
fun main(args : Array<String>) {
    // Create shuffled mix
    val random = SingleThreadSplittableRandomAdapter()
    val folder = Paths.get(if (args.isEmpty()) {""} else {args[0]}).toAbsolutePath().toFile()
    println("Working in $folder")
    val listFiles = folder.listFiles {
            file -> file.isFile
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
    lists.sortByDescending { it.size } // draw from longest list first
    val listPositions = MutableList(lists.size) {0}
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
        merged.add(nextVideo)
        println(nextVideo)
        output++
        listPositions[bestIndex]++
        lastChoice = bestIndex
    }
    // Log into YouTube
    /*
    val httpClient = HttpClient.newHttpClient()
    val loginUrlRequest = HttpRequest.newBuilder(DEVICE_LOGIN_ENDPOINT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("client_id=$CLIENT_ID&scope=email%20profile%20clientid%20../auth/youtube"))
        .build()
    val loginRawResponse = httpClient.send(loginUrlRequest, TO_INPUT_STREAM)
    val json: LoginUrlResponse =
        JSON_FACTORY.createJsonParser(loginRawResponse.body()).parse(LoginUrlResponse::class.java, true) as LoginUrlResponse
    println("Visit this page: ${json.verification_url}")
    println("Then enter this code: ${json.user_code}")
    val tokenRequest = HttpRequest.newBuilder(TOKEN_ENDPOINT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(
            "client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&code=${json.device_code}&grant_type=http://oauth.net/grant_type/device/1.0"))
        .build()
    lateinit var token: TokenResponse
    do {
        val tokenRawResponse = httpClient.send(tokenRequest, TO_INPUT_STREAM)
        token = JSON_FACTORY.createJsonParser(tokenRawResponse.body()).parse(TokenResponse::class.java, true) as TokenResponse
        if (token.error != null) {
            Thread.sleep(1000 * json.interval)
        } else {
            break
        }
    } while (true)

     */
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
    val playlistItems = service.playlistItems()

    // Delete old contents
    val listRequest = playlistItems.list("id")
    listRequest.playlistId = PLAYLIST_ID
    listRequest.maxResults = MAX_RESULTS
    var nextPageToken: String? = null
    do {
        listRequest.pageToken = nextPageToken
        val listResponse = listRequest.setKey(DEVELOPER_KEY).execute()
        val items = listResponse.items
        nextPageToken = listResponse.prevPageToken
        if (items.isEmpty() && nextPageToken.isNullOrEmpty()) {
            error(listResponse)
        }
        for (video in items) {
            playlistItems.delete(video.id).setKey(DEVELOPER_KEY).execute()
        }
    } while (nextPageToken != null)

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
        playlistItems.insert("snippet", playlistItem).setKey(DEVELOPER_KEY).execute()
    }
}