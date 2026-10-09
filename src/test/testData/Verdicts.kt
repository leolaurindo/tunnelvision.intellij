class VerdictsSample {
    fun count(guids: List<Int>): Report {
        val settings = getSettings()

        val verdicts = guids.distinct().chunked(10).flatMap { batch ->
            batch.map { it to (it > settings.threshold) }
        }

        return Report(
            total = verdicts.size,
            threshold = settings.threshold,
            counts = verdicts.groupingBy { it.second }.eachCount(),
            selected = verdicts.filter { it.second }.map { it.first }
        )
    }

    fun countOther(guids: List<Int>): Report {
        val settings = getSettings()

        val verdicts = guids.distinct().chunked(10).flatMap { batch ->
            batch.map { it to (it < settings.threshold) }
        }

        return Report(
            total = guids.distinct().size,
            threshold = settings.threshold,
            counts = verdicts.groupingBy { it.second }.eachCount(),
            selected = verdicts.filter { it.second }.map { it.first },
            excluded = verdicts.filter { !it.second }.map { it.first }
        )
    }

    private fun getSettings() = Settings(threshold = 5)
}

data class Settings(val threshold: Int)

data class Report(
    val total: Int,
    val threshold: Int,
    val counts: Map<Boolean, Int>,
    val selected: List<Int>,
    val excluded: List<Int> = emptyList(),
)
