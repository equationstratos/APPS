package com.survival.morse

import kotlin.random.Random

/** Order in which the Koch method introduces the characters (G4FON). */
const val KOCH_ORDER = "KMRSUAPTLOWI.NJEF0YV,G5/Q9ZH38B?427C1D6X"
const val MIN_KOCH_LEVEL = 2
val MAX_KOCH_LEVEL: Int = KOCH_ORDER.length

/** A new character is added after [PASS_CORRECT] good answers among the last [PASS_WINDOW]. */
const val PASS_WINDOW = 20
const val PASS_CORRECT = 18
private const val MAX_CHOICES = 4
private const val NEWEST_WEIGHT = 3.0
private const val MISS_WEIGHT = 1.0
private const val MAX_MISS_BONUS = 3

class KochQuestion(val answer: Char, val choices: List<Char>)

class KochResult(val correct: Boolean, val leveledUp: Boolean)

/**
 * Simplified Koch trainer: characters are heard, never counted. The newest character and the ones
 * recently missed come up more often; the next character unlocks at 90 % over 20 answers.
 */
class KochTrainer(level: Int, private val random: Random = Random.Default) {
    var level: Int = level.coerceIn(MIN_KOCH_LEVEL, MAX_KOCH_LEVEL)
        private set

    var correct = 0
        private set
    var total = 0
        private set
    var streak = 0
        private set

    private val recent = ArrayDeque<Boolean>()
    private val misses = mutableMapOf<Char, Int>()

    val active: List<Char> get() = KOCH_ORDER.take(level).toList()
    val newest: Char get() = KOCH_ORDER[level - 1]
    val recentCorrect: Int get() = recent.count { it }
    val recentCount: Int get() = recent.size

    fun next(previous: Char? = null): KochQuestion {
        val pool = active
        val candidates = pool.filter { it != previous }.ifEmpty { pool }
        val weights = candidates.map { char ->
            var weight = 1.0
            if (char == newest && level > MIN_KOCH_LEVEL) weight *= NEWEST_WEIGHT
            weight + MISS_WEIGHT * (misses[char] ?: 0)
        }
        var pick = random.nextDouble() * weights.sum()
        var answer = candidates.last()
        for ((index, weight) in weights.withIndex()) {
            pick -= weight
            if (pick < 0.0) {
                answer = candidates[index]
                break
            }
        }
        val others = pool.filter { it != answer }.shuffled(random).take(minOf(MAX_CHOICES, pool.size) - 1)
        return KochQuestion(answer, (others + answer).shuffled(random))
    }

    fun answer(question: KochQuestion, given: Char): KochResult {
        val ok = given == question.answer
        total++
        if (ok) {
            correct++
            streak++
            val missed = misses[question.answer] ?: 0
            if (missed <= 1) {
                misses.remove(question.answer)
            } else {
                misses[question.answer] = missed - 1
            }
        } else {
            streak = 0
            misses[question.answer] = minOf(MAX_MISS_BONUS, (misses[question.answer] ?: 0) + 1)
        }
        recent.addLast(ok)
        while (recent.size > PASS_WINDOW) recent.removeFirst()
        val leveledUp = recent.size >= PASS_WINDOW && recentCorrect >= PASS_CORRECT && level < MAX_KOCH_LEVEL
        if (leveledUp) changeLevel(level + 1)
        return KochResult(ok, leveledUp)
    }

    fun changeLevel(newLevel: Int) {
        val target = newLevel.coerceIn(MIN_KOCH_LEVEL, MAX_KOCH_LEVEL)
        if (target == level) return
        level = target
        recent.clear()
        misses.keys.retainAll(active.toSet())
    }

    fun resetScore() {
        correct = 0
        total = 0
        streak = 0
        recent.clear()
    }
}
