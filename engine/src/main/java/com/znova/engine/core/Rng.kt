
package com.znova.engine.core

class Rng(
    private val seed: Long,
    initialDrawCount: Long = 0L
) {
    var draws: Long = initialDrawCount
        private set

    fun nextLong(): Long {
        draws++

        var value = seed + draws * -0x61c8864680b583ebL

        value = (value xor (value ushr 30)) * -0x40a7b892e31b1a47L
        value = (value xor (value ushr 27)) * -0x6b2fb644ecceee15L

        return value xor (value ushr 31)
    }

    fun nextDouble(): Double {
        return (nextLong() ushr 11) * (1.0 / (1L shl 53))
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive" }

        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun chance(probability: Double): Boolean {
        return nextDouble() < probability
    }

    fun <T> pick(items: List<T>): T {
        return items[nextInt(items.size)]
    }

    fun <T> weighted(
        items: List<T>,
        weight: (T) -> Double
    ): T? {
        var totalWeight = 0.0

        for (item in items) {
            totalWeight += weight(item).coerceAtLeast(0.0)
        }

        if (totalWeight <= 0.0) return null

        var randomWeight = nextDouble() * totalWeight

        for (item in items) {
            randomWeight -= weight(item).coerceAtLeast(0.0)

            if (randomWeight < 0.0) return item
        }

        return items.last()
    }
}
