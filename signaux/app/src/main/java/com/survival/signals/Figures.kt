package com.survival.signals

/*
 * Geometry of the drawings, independent of Android. Sketches live in a unit square (x to the right,
 * y downwards, 0..1); timelines are lists of "on" spans along a time axis.
 */

class Pt(val x: Float, val y: Float)

class Seg(val a: Pt, val b: Pt)

class Circle(val center: Pt, val radius: Float)

/**
 * A line drawing in the unit square.
 *
 * @property lines main strokes.
 * @property ghosts alternate positions of a moving limb, drawn faded.
 * @property heads filled circles.
 * @property guides dashed helper lines.
 * @property flames positions of flame symbols.
 * @property weight stroke width as a fraction of the drawing size.
 * @property ground whether the strokes are drawn on a ground-coloured tile.
 */
class Sketch(
    val lines: List<Seg>,
    val ghosts: List<Seg> = emptyList(),
    val heads: List<Circle> = emptyList(),
    val guides: List<Seg> = emptyList(),
    val flames: List<Pt> = emptyList(),
    val weight: Float,
    val ground: Boolean,
)

/** A stretch of time during which the signal is on, in the timeline's unit (seconds or Morse units). */
class Span(val start: Float, val end: Float) {
    val length: Float get() = end - start
}

/** A label under the time axis, centred on [at]. */
class TimeMark(val at: Float, val label: String)

/**
 * @property length total duration shown on the axis.
 * @property modulated draws each span as a rising and falling wave (siren) instead of a solid bar.
 * @property waveCycles number of wave periods drawn inside each modulated span.
 */
class Timeline(
    val spans: List<Span>,
    val length: Float,
    val marks: List<TimeMark>,
    val caption: String,
    val modulated: Boolean = false,
    val waveCycles: Int = 0,
)

private fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = Seg(Pt(x1, y1), Pt(x2, y2))

private const val GROUND_WEIGHT = 0.11f
private const val BODY_WEIGHT = 0.065f

private val SHOULDER = Pt(0.5f, 0.34f)
private val HIP = Pt(0.5f, 0.62f)

/** Head, torso and legs shared by all silhouettes. */
private val BODY_HEAD = Circle(Pt(0.5f, 0.2f), 0.085f)
private val BODY_TRUNK = listOf(
    Seg(SHOULDER, HIP),
    Seg(HIP, Pt(0.4f, 0.92f)),
    Seg(HIP, Pt(0.6f, 0.92f)),
)

private fun arm(toX: Float, toY: Float) = Seg(SHOULDER, Pt(toX, toY))

/** Line drawing of [figure], or null when the figure is a timeline. */
fun sketchOf(figure: Figure): Sketch? = when (figure) {
    Figure.GROUND_V -> Sketch(
        lines = listOf(seg(0.22f, 0.18f, 0.5f, 0.82f), seg(0.5f, 0.82f, 0.78f, 0.18f)),
        weight = GROUND_WEIGHT,
        ground = true,
    )
    Figure.GROUND_X -> Sketch(
        lines = listOf(seg(0.22f, 0.18f, 0.78f, 0.82f), seg(0.78f, 0.18f, 0.22f, 0.82f)),
        weight = GROUND_WEIGHT,
        ground = true,
    )
    Figure.GROUND_N -> Sketch(
        lines = listOf(
            seg(0.26f, 0.82f, 0.26f, 0.18f),
            seg(0.26f, 0.18f, 0.74f, 0.82f),
            seg(0.74f, 0.82f, 0.74f, 0.18f),
        ),
        weight = GROUND_WEIGHT,
        ground = true,
    )
    Figure.GROUND_Y -> Sketch(
        lines = listOf(
            seg(0.22f, 0.18f, 0.5f, 0.5f),
            seg(0.78f, 0.18f, 0.5f, 0.5f),
            seg(0.5f, 0.5f, 0.5f, 0.82f),
        ),
        weight = GROUND_WEIGHT,
        ground = true,
    )
    Figure.GROUND_ARROW -> Sketch(
        lines = listOf(
            seg(0.5f, 0.84f, 0.5f, 0.18f),
            seg(0.5f, 0.18f, 0.28f, 0.42f),
            seg(0.5f, 0.18f, 0.72f, 0.42f),
        ),
        weight = GROUND_WEIGHT,
        ground = true,
    )
    Figure.BODY_YES -> Sketch(
        lines = BODY_TRUNK + listOf(arm(0.24f, 0.06f), arm(0.76f, 0.06f)),
        heads = listOf(BODY_HEAD),
        weight = BODY_WEIGHT,
        ground = false,
    )
    Figure.BODY_NO -> Sketch(
        lines = BODY_TRUNK + listOf(arm(0.78f, 0.08f), arm(0.22f, 0.6f)),
        heads = listOf(BODY_HEAD),
        weight = BODY_WEIGHT,
        ground = false,
    )
    Figure.BODY_SEA -> Sketch(
        lines = BODY_TRUNK + listOf(arm(0.12f, 0.34f), arm(0.88f, 0.34f)),
        ghosts = listOf(arm(0.17f, 0.16f), arm(0.83f, 0.16f), arm(0.17f, 0.52f), arm(0.83f, 0.52f)),
        heads = listOf(BODY_HEAD),
        weight = BODY_WEIGHT,
        ground = false,
    )
    Figure.FIRES_TRIANGLE -> {
        val top = Pt(0.5f, 0.22f)
        val left = Pt(0.2f, 0.74f)
        val right = Pt(0.8f, 0.74f)
        Sketch(
            lines = emptyList(),
            guides = listOf(Seg(top, left), Seg(left, right), Seg(right, top)),
            flames = listOf(top, left, right),
            weight = 0.02f,
            ground = true,
        )
    }
    else -> null
}

/** Morse unit: a dash lasts three dots; elements of one sign are separated by one unit. */
const val MORSE_DASH_UNITS = 3

/**
 * "On" spans of a Morse sequence written with '.' and '-'. A space adds a letter gap (3 units),
 * a '/' a word gap (7 units). The distress signal "...---..." is sent as one sign, without letter gaps.
 */
fun morseSpans(code: String): List<Span> {
    val spans = ArrayList<Span>()
    var t = 0f
    var gap = 0f
    for (c in code) {
        when (c) {
            '.', '-' -> {
                if (spans.isNotEmpty()) t += maxOf(gap, 1f)
                val units = if (c == '.') 1f else MORSE_DASH_UNITS.toFloat()
                spans += Span(t, t + units)
                t += units
                gap = 0f
            }
            ' ' -> gap = maxOf(gap, 3f)
            '/' -> gap = maxOf(gap, 7f)
            else -> throw IllegalArgumentException("Caractère Morse inconnu : $c")
        }
    }
    return spans
}

const val SOS_MORSE = "...---..."

/** Siren cycle of the French national alert signal: 1 min 41 s, cycles separated by 5 s. */
const val SIREN_CYCLE_S = 101f
const val SIREN_GAP_S = 5f
const val SIREN_CYCLES = 3
const val SIREN_END_S = 30f

/** Dam horn: 2 s of sound, 3 s of silence, for at least 2 minutes. */
const val DAM_ON_S = 2f
const val DAM_OFF_S = 3f
const val DAM_MIN_S = 120f

private fun everyMinute(count: Int): List<Span> = List(count) { k ->
    val start = k * 60f / count
    Span(start, start + 1f)
}

private val TWO_MINUTE_MARKS = listOf(TimeMark(0f, "0 s"), TimeMark(60f, "1 min"), TimeMark(90f, "pause"), TimeMark(120f, "2 min"))

/** Time diagram of [figure], or null when the figure is a sketch. */
fun timelineOf(figure: Figure): Timeline? = when (figure) {
    Figure.TIMELINE_SOS -> {
        val spans = morseSpans(SOS_MORSE)
        Timeline(
            spans = spans,
            length = spans.last().end,
            marks = emptyList(),
            caption = frenchTypography("Bref = 1 temps, long = 3 temps, 1 temps de silence entre deux signaux."),
        )
    }
    Figure.TIMELINE_ALPINE_CALL -> Timeline(
        spans = everyMinute(6),
        length = 120f,
        marks = TWO_MINUTE_MARKS,
        caption = frenchTypography("Un signal toutes les 10 s pendant 1 minute, puis 1 minute de pause."),
    )
    Figure.TIMELINE_ALPINE_REPLY -> Timeline(
        spans = everyMinute(3),
        length = 120f,
        marks = TWO_MINUTE_MARKS,
        caption = frenchTypography("Un signal toutes les 20 s pendant 1 minute, puis 1 minute de pause."),
    )
    Figure.TIMELINE_SIREN -> {
        val spans = List(SIREN_CYCLES) { k ->
            val start = k * (SIREN_CYCLE_S + SIREN_GAP_S)
            Span(start, start + SIREN_CYCLE_S)
        }
        Timeline(
            spans = spans,
            length = spans.last().end,
            marks = spans.map { TimeMark((it.start + it.end) / 2f, "1 min 41 s") },
            caption = frenchTypography("Son modulé : 3 cycles de 1 min 41 s, séparés par 5 s de silence."),
            modulated = true,
            waveCycles = 5,
        )
    }
    Figure.TIMELINE_SIREN_END -> Timeline(
        spans = listOf(Span(0f, SIREN_END_S)),
        length = SIREN_END_S,
        marks = listOf(TimeMark(0f, "0 s"), TimeMark(SIREN_END_S, "30 s")),
        caption = frenchTypography("Son continu de 30 s."),
    )
    Figure.TIMELINE_DAM -> {
        val period = DAM_ON_S + DAM_OFF_S
        val count = (DAM_MIN_S / period).toInt()
        Timeline(
            spans = List(count) { k -> Span(k * period, k * period + DAM_ON_S) },
            length = DAM_MIN_S,
            marks = listOf(TimeMark(0f, "0 s"), TimeMark(60f, "1 min"), TimeMark(DAM_MIN_S, "2 min")),
            caption = frenchTypography("2 s de son, 3 s de silence, pendant au moins 2 minutes."),
        )
    }
    else -> null
}
