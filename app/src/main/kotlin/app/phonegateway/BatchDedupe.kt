package app.phonegateway

class BatchSplit(val fresh: List<BatchItem>, val known: List<BatchItem>) {
    val isReplay: Boolean get() = fresh.isEmpty() && known.isNotEmpty()
}

object BatchDedupe {
    fun split(items: List<BatchItem>, isKnown: (String) -> Boolean): BatchSplit {
        val fresh = ArrayList<BatchItem>()
        val known = ArrayList<BatchItem>()
        for (item in items) {
            val id = item.id
            if (id != null && isKnown(id)) known.add(item) else fresh.add(item)
        }
        return BatchSplit(fresh, known)
    }
}
