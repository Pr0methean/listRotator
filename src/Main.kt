import io.github.pr0methean.betterrandom.prng.concurrent.SingleThreadSplittableRandomAdapter
import java.nio.file.Paths

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
            .map { line -> line.split("#")[0] }
            .filter { line -> line.isNotBlank() }
            .shuffled(random)
        if (lines.isNotEmpty()) {
            lists.add(lines)
        }
        totalEntries += lines.size
    }
    val listPositions = MutableList(lists.size) {0}
    var output = 0
    var lastChoice = -1
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
        println(lists[bestIndex][listPositions[bestIndex]])
        output++
        listPositions[bestIndex]++
        lastChoice = bestIndex
    }
}