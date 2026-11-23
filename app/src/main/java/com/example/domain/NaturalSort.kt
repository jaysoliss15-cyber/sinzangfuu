package com.example.domain

object NaturalSort {
  private val tokenRegex = Regex("(\\d+|\\D+)")

  val comparator: Comparator<String> = Comparator { a, b ->
    val tokensA = tokenRegex.findAll(a.lowercase()).map { it.value }.toList()
    val tokensB = tokenRegex.findAll(b.lowercase()).map { it.value }.toList()
    val count = minOf(tokensA.size, tokensB.size)

    for (i in 0 until count) {
      val ta = tokensA[i]
      val tb = tokensB[i]
      val numA = ta.toBigIntegerOrNull()
      val numB = tb.toBigIntegerOrNull()
      val cmp =
        if (numA != null && numB != null) {
          numA.compareTo(numB)
        } else {
          ta.compareTo(tb)
        }
      if (cmp != 0) return@Comparator cmp
    }
    tokensA.size.compareTo(tokensB.size)
  }

  fun <T> sortedNaturally(items: Iterable<T>, selector: (T) -> String): List<T> =
    items.sortedWith { left, right -> comparator.compare(selector(left), selector(right)) }
}
