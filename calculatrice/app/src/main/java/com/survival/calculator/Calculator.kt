package com.survival.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private const val MAX_INPUT_DIGITS = 15
private const val DISPLAY_DIGITS = 12
private const val CANCELLATION_DIGITS = 25
private const val NARROW_NBSP = '\u202F'
private const val MINUS = "\u2212"

private val MATH = MathContext.DECIMAL128
private val DISPLAY_MATH = MathContext(DISPLAY_DIGITS, RoundingMode.HALF_UP)
private val EXPRESSION_MATH = MathContext(MAX_INPUT_DIGITS, RoundingMode.HALF_UP)
private val OVERFLOW = BigDecimal.ONE.scaleByPowerOfTen(100)
private val UNDERFLOW = BigDecimal.ONE.scaleByPowerOfTen(-100)
private val SCIENTIFIC_ABOVE = BigDecimal.ONE.scaleByPowerOfTen(15)
private val SCIENTIFIC_BELOW = BigDecimal.ONE.scaleByPowerOfTen(-9)

enum class Op(val symbol: String, val spokenName: String) {
    ADD("+", "Plus"),
    SUBTRACT("−", "Moins"),
    MULTIPLY("×", "Multiplier"),
    DIVIDE("÷", "Diviser"),
}

sealed interface Key {
    data class Digit(val digit: Char) : Key
    data class Operator(val op: Op) : Key
    data object Decimal : Key
    data object Backspace : Key
    data object Clear : Key
    data object Negate : Key
    data object Percent : Key
    data object Equals : Key
}

/**
 * Immutable calculator state. Numbers stay [BigDecimal] and the operand being typed stays a
 * '.'-based string ([entry]); only [displayText] and [expression] are French-formatted, and
 * they are never parsed back.
 */
data class CalcState(
    val entry: String? = null,
    val value: BigDecimal = BigDecimal.ZERO,
    val accumulator: BigDecimal? = null,
    val pending: Op? = null,
    val awaitingOperand: Boolean = false,
    val repeatOp: Op? = null,
    val repeatOperand: BigDecimal? = null,
    val expression: String = "",
    val error: Boolean = false,
) {
    val showsAllClear: Boolean
        get() = error || entry == null || entry == "0"

    val displayText: String
        get() {
            val text = entry
            return when {
                error -> "Erreur"
                text != null -> formatEntry(text)
                else -> formatValue(value)
            }
        }

    private val current: BigDecimal
        get() = entry?.let { BigDecimal(it) } ?: value

    fun press(key: Key): CalcState {
        if (error) {
            val fresh = CalcState()
            return if (key is Key.Digit || key == Key.Decimal) fresh.press(key) else fresh
        }
        return when (key) {
            is Key.Digit -> inputDigit(key.digit)
            is Key.Operator -> applyOperator(key.op)
            Key.Decimal -> inputDecimal()
            Key.Backspace -> backspace()
            Key.Clear -> clear()
            Key.Negate -> negate()
            Key.Percent -> percent()
            Key.Equals -> evaluate()
        }
    }

    private fun beginEntry(text: String): CalcState = copy(
        entry = text,
        awaitingOperand = false,
        expression = if (pending == null) "" else expression,
    )

    private fun inputDigit(digit: Char): CalcState {
        val text = entry ?: return beginEntry(digit.toString())
        if (significantDigits(text) >= MAX_INPUT_DIGITS) return this
        val next = when (text) {
            "0" -> digit.toString()
            "-0" -> "-$digit"
            else -> text + digit
        }
        return copy(entry = next)
    }

    private fun inputDecimal(): CalcState {
        val text = entry ?: return beginEntry("0.")
        if ('.' in text || significantDigits(text) >= MAX_INPUT_DIGITS) return this
        return copy(entry = "$text.")
    }

    private fun backspace(): CalcState {
        val text = entry ?: return this
        val next = text.dropLast(1)
        return copy(entry = if (next.isEmpty() || next == "-") "0" else next)
    }

    private fun clear(): CalcState = if (showsAllClear) CalcState() else copy(entry = "0")

    private fun negate(): CalcState {
        val text = entry
        return when {
            text != null -> copy(entry = if (text.startsWith('-')) text.substring(1) else "-$text")
            awaitingOperand -> beginEntry("-0")
            else -> copy(value = value.negate(), expression = if (pending == null) "" else expression)
        }
    }

    private fun percent(): CalcState {
        val x = current
        val left = accumulator
        // "50 + 10 %" means 10 % of 50, as on every pocket calculator.
        val raw = if (left != null && (pending == Op.ADD || pending == Op.SUBTRACT)) {
            left.multiply(x, MATH).movePointLeft(2)
        } else {
            x.movePointLeft(2)
        }
        val result = normalize(raw) ?: return CalcState(error = true)
        return copy(
            entry = null,
            value = result,
            awaitingOperand = false,
            expression = if (pending == null) "" else expression,
        )
    }

    private fun applyOperator(op: Op): CalcState {
        val previous = pending
        val left = accumulator
        val base = if (previous != null && left != null && !awaitingOperand) {
            val right = current
            compute(previous, left, right) ?: return CalcState(
                error = true,
                expression = "${expressionNumber(left)} ${previous.symbol} ${formatOperand(right)}",
            )
        } else {
            current
        }
        return copy(
            entry = null,
            value = base,
            accumulator = base,
            pending = op,
            awaitingOperand = true,
            expression = "${expressionNumber(base)} ${op.symbol}",
        )
    }

    private fun evaluate(): CalcState {
        val op = pending
        val left = accumulator
        if (op != null && left != null) {
            return resultOf(op, left, if (awaitingOperand) left else current)
        }
        val lastOp = repeatOp
        val lastOperand = repeatOperand
        if (lastOp != null && lastOperand != null) {
            return resultOf(lastOp, current, lastOperand)
        }
        return copy(entry = null, value = current)
    }

    private fun resultOf(op: Op, left: BigDecimal, right: BigDecimal): CalcState {
        val text = "${expressionNumber(left)} ${op.symbol} ${formatOperand(right)} ="
        val result = compute(op, left, right) ?: return CalcState(error = true, expression = text)
        return CalcState(value = result, repeatOp = op, repeatOperand = right, expression = text)
    }
}

/** Returns null when the operation has no displayable result (division by zero, overflow). */
private fun compute(op: Op, left: BigDecimal, right: BigDecimal): BigDecimal? {
    val raw = when (op) {
        Op.ADD -> dropCancellationNoise(left.add(right, MATH), left, right)
        Op.SUBTRACT -> dropCancellationNoise(left.subtract(right, MATH), left, right)
        Op.MULTIPLY -> left.multiply(right, MATH)
        Op.DIVIDE -> if (right.signum() == 0) return null else left.divide(right, MATH)
    }
    return normalize(raw)
}

// 1 ÷ 3 × 3 − 1 leaves a residue at the 34th digit: it is rounding noise, not a real value.
private fun dropCancellationNoise(result: BigDecimal, left: BigDecimal, right: BigDecimal): BigDecimal {
    val magnitude = left.abs().max(right.abs())
    return if (result.abs() < magnitude.movePointLeft(CANCELLATION_DIGITS)) BigDecimal.ZERO else result
}

private fun normalize(x: BigDecimal): BigDecimal? {
    val abs = x.abs()
    return when {
        abs >= OVERFLOW -> null
        abs < UNDERFLOW -> BigDecimal.ZERO
        else -> x
    }
}

private fun significantDigits(entry: String): Int {
    val leadingZero = if (entry.removePrefix("-").startsWith("0")) 1 else 0
    return entry.count { it.isDigit() } - leadingZero
}

private fun formatValue(x: BigDecimal, precision: MathContext = DISPLAY_MATH): String {
    if (x.signum() == 0) return "0"
    val rounded = x.round(precision).stripTrailingZeros()
    val abs = rounded.abs()
    val sign = if (rounded.signum() < 0) MINUS else ""
    if (abs >= SCIENTIFIC_ABOVE || abs < SCIENTIFIC_BELOW) {
        val exponent = abs.precision() - abs.scale() - 1
        val mantissa = abs.movePointLeft(exponent).stripTrailingZeros().toPlainString().replace('.', ',')
        val exponentText = if (exponent < 0) "$MINUS${-exponent}" else exponent.toString()
        return "$sign${mantissa}E$exponentText"
    }
    return sign + groupDigits(abs.toPlainString())
}

// The expression line shows operands with the full input precision so typed values are not altered.
private fun expressionNumber(x: BigDecimal): String = formatValue(x, EXPRESSION_MATH)

private fun formatOperand(x: BigDecimal): String {
    val text = expressionNumber(x)
    return if (x.signum() < 0) "($text)" else text
}

private fun formatEntry(entry: String): String {
    val negative = entry.startsWith('-')
    val body = if (negative) entry.substring(1) else entry
    return (if (negative) MINUS else "") + groupDigits(body)
}

/** Groups the integer part of a '.'-based plain number and switches to a decimal comma. */
private fun groupDigits(plain: String): String {
    val integer = plain.substringBefore('.')
    val grouped = integer.reversed().chunked(3).joinToString(NARROW_NBSP.toString()).reversed()
    return if ('.' in plain) grouped + "," + plain.substringAfter('.') else grouped
}
