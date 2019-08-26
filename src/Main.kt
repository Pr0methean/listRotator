import io.github.pr0methean.betterrandom.prng.concurrent.SingleThreadSplittableRandomAdapter
import java.nio.file.Paths
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory

import com.google.api.services.youtube.YouTube

import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Arrays

fun main(args : Array<String>) {
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
    lists.sortBy { -it.size } // draw from longest list first
    val listPositions = MutableList(lists.size) {0}
    var output = 0
    var lastChoice = -1
    val merged = mutableListOf<String>()
    while (output < totalEntries) {
        var bestIndex = -1
        var lowestFractionDone = 1.0
        for (i in lists.indices) {
            if (i == lastChoice) {
                continue
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
        merged.add(lists[bestIndex][listPositions[bestIndex]])
        output++
        listPositions[bestIndex]++
        lastChoice = bestIndex
    }
    val playlistItems = service.playlistItems()
    // Delete existing contents
    val listRequest = playlistItems.list(PLAYLIST_ID)
    listRequest.setKey(DEVELOPER_KEY).execute()
    // Define and execute the API request
    val request = playlistItems.delete("YOUR_PLAYLIST_ITEM_ID")
    request.setKey(DEVELOPER_KEY).execute()
}