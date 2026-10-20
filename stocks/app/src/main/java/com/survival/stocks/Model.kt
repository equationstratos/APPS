package com.survival.stocks

import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// Pure inventory model: no Android dependency, covered by the JVM tests.

const val MAX_QUANTITY = 1_000_000.0
const val MAX_LITERS_PER_UNIT = 1_000.0
const val MAX_KCAL = 100_000.0
const val MAX_NAME = 80
const val MAX_UNIT = 20
const val MAX_LOCATION = 60
const val MAX_NOTE = 300
const val MAX_PERSONS = 50

/** French month names, used instead of the platform locale data so the output never varies. */
val MONTHS = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre"
)

enum class Category(
    val key: String,
    val label: String,
    val hint: String,
    val defaultUnit: String,
    val units: List<String>,
) {
    WATER("water", "Eau", "Bouteilles, bidons, réserves d'eau potable", "L", listOf("L", "bouteilles", "packs", "bidons", "mL")),
    FOOD(
        "food", "Nourriture", "Conserves, féculents, biscuits, plats préparés…", "boîtes",
        listOf("boîtes", "paquets", "sachets", "bocaux", "kg", "g", "unités")
    ),
    MEDS("meds", "Médicaments", "Trousse de secours, traitements, pansements", "boîtes", listOf("boîtes", "unités", "flacons", "tubes")),
    HYGIENE("hygiene", "Hygiène", "Savon, papier toilette, protections…", "unités", listOf("unités", "paquets", "rouleaux", "flacons")),
    ENERGY("energy", "Énergie", "Piles, gaz, combustible", "unités", listOf("unités", "piles", "cartouches", "L", "kg")),
    TOOLS("tools", "Outils", "Lampe, radio, couteau, ruban adhésif…", "unités", listOf("unités")),
    OTHER("other", "Autre", "Tout le reste", "unités", listOf("unités", "paquets", "boîtes"));

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/**
 * Expiry date: either a precise day ("DLC" / "à consommer jusqu'au") or only a month and a year
 * (many "DDM" read "à consommer de préférence avant fin 03/2027"), valid until the end of that month.
 */
data class Expiry(val year: Int, val month: Int, val day: Int? = null) {
    init {
        require(year in 1900..2200) { "year" }
        require(month in 1..12) { "month" }
        if (day != null) require(day in 1..YearMonth.of(year, month).lengthOfMonth()) { "day" }
    }

    val monthOnly: Boolean get() = day == null

    /** Last day on which the product is still within its date. */
    fun lastDay(): LocalDate = if (day == null) YearMonth.of(year, month).atEndOfMonth() else LocalDate.of(year, month, day)

    fun iso(): String = buildString {
        append(year.toString().padStart(4, '0')).append('-').append(month.toString().padStart(2, '0'))
        if (day != null) append('-').append(day.toString().padStart(2, '0'))
    }

    /** "15/03/2027" or "03/2027". */
    fun short(): String {
        val m = month.toString().padStart(2, '0')
        return if (day == null) "$m/$year" else "${day.toString().padStart(2, '0')}/$m/$year"
    }

    /** "15 mars 2027", "1er mars 2027" or "mars 2027". */
    fun long(): String {
        val m = MONTHS[month - 1]
        return when (day) {
            null -> "$m $year"
            1 -> "1er $m $year"
            else -> "$day $m $year"
        }
    }

    /** Same validity end, expressed by month only or by precise day (the month's last day). */
    fun withMonthOnly(monthOnly: Boolean): Expiry = when {
        monthOnly && day != null -> Expiry(year, month, null)
        !monthOnly && day == null -> Expiry(year, month, lastDay().dayOfMonth)
        else -> this
    }

    companion object {
        fun of(date: LocalDate, monthOnly: Boolean): Expiry =
            Expiry(date.year, date.monthValue, if (monthOnly) null else date.dayOfMonth)

        /** Parses "YYYY-MM" or "YYYY-MM-DD"; null for anything else. */
        fun parseIso(text: String?): Expiry? {
            val t = text?.trim().orEmpty()
            val m = Regex("""^(\d{4})-(\d{2})(?:-(\d{2}))?$""").matchEntire(t) ?: return null
            val (y, mo, d) = m.destructured
            return runCatching { Expiry(y.toInt(), mo.toInt(), d.takeIf { it.isNotEmpty() }?.toInt()) }.getOrNull()
        }
    }
}

data class Item(
    val id: Long,
    val name: String,
    val category: Category,
    val quantity: Double,
    val unit: String,
    val expiry: Expiry? = null,
    val location: String = "",
    val note: String = "",
    /** Water only, for units that are not a volume (bottles, packs…): litres in one unit. */
    val litersPerUnit: Double? = null,
    /** Food only: kcal per unit, or per 100 g / 100 mL for mass and volume units. */
    val kcalPerUnit: Double? = null,
) {
    /** Everything but the id, to recognise the same article in an imported backup. */
    fun contentKey(): Item = copy(id = 0, name = name.trim(), unit = unit.trim(), location = location.trim(), note = note.trim())
}

data class Settings(
    val persons: Int = 1,
    val waterPerDay: Double = DEFAULT_WATER_PER_DAY,
    val kcalPerDay: Double = DEFAULT_KCAL_PER_DAY,
    val targetDays: Int = DEFAULT_TARGET_DAYS,
    val soonDays: Int = DEFAULT_SOON_DAYS,
) {
    fun sanitized(): Settings = Settings(
        persons = persons.coerceIn(1, MAX_PERSONS),
        waterPerDay = waterPerDay.finiteOr(DEFAULT_WATER_PER_DAY).coerceIn(MIN_WATER_PER_DAY, MAX_WATER_PER_DAY),
        kcalPerDay = kcalPerDay.finiteOr(DEFAULT_KCAL_PER_DAY).coerceIn(MIN_KCAL_PER_DAY, MAX_KCAL_PER_DAY),
        targetDays = targetDays.coerceIn(1, MAX_TARGET_DAYS),
        soonDays = soonDays.coerceIn(0, MAX_SOON_DAYS),
    )

    val usesDefaultNeeds: Boolean get() = waterPerDay == DEFAULT_WATER_PER_DAY && kcalPerDay == DEFAULT_KCAL_PER_DAY

    companion object {
        /** Government 72-hour emergency kit: 6 L of water per person, i.e. 2 L per person per day. */
        const val DEFAULT_WATER_PER_DAY = 2.0

        /** EU reference intake for an average adult (Regulation (EU) 1169/2011, annex XIII): 2 000 kcal. */
        const val DEFAULT_KCAL_PER_DAY = 2000.0
        const val DEFAULT_TARGET_DAYS = 3
        const val DEFAULT_SOON_DAYS = 30
        const val MIN_WATER_PER_DAY = 0.1
        const val MAX_WATER_PER_DAY = 20.0
        const val MIN_KCAL_PER_DAY = 100.0
        const val MAX_KCAL_PER_DAY = 10_000.0
        const val MAX_TARGET_DAYS = 365
        const val MAX_SOON_DAYS = 365
        val TARGET_CHOICES = listOf(3, 7, 14, 30)
    }
}

data class Inventory(
    val items: List<Item> = emptyList(),
    val settings: Settings = Settings(),
    val nextId: Long = 1,
) {
    fun item(id: Long): Item? = items.firstOrNull { it.id == id }

    /** Adds [item] with a fresh id; returns the new inventory and that id. */
    fun add(item: Item): Pair<Inventory, Long> {
        val id = nextId
        return copy(items = items + item.copy(id = id), nextId = id + 1) to id
    }

    fun replace(item: Item): Inventory = copy(items = items.map { if (it.id == item.id) item else it })

    /** Removes the article; returns the new inventory, the removed article and its position (for undo). */
    fun remove(id: Long): Triple<Inventory, Item, Int>? {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return null
        return Triple(copy(items = items.filterIndexed { i, _ -> i != index }), items[index], index)
    }

    /** Puts back an article removed by [remove], at its former position, unless its id is used again. */
    fun restore(item: Item, index: Int): Inventory {
        if (items.any { it.id == item.id }) return this
        val list = items.toMutableList()
        list.add(index.coerceIn(0, list.size), item)
        return copy(items = list, nextId = maxOf(nextId, item.id + 1))
    }

    fun adjustQuantity(id: Long, delta: Double): Inventory = copy(
        items = items.map {
            if (it.id == id) it.copy(quantity = roundQuantity((it.quantity + delta).coerceIn(0.0, MAX_QUANTITY))) else it
        }
    )

    fun withSettings(settings: Settings): Inventory = copy(settings = settings.sanitized())

    /** Repairs anything a damaged or hand-edited file may contain. */
    fun sanitized(): Inventory {
        val seen = HashSet<Long>()
        var next = maxOf(nextId, (items.maxOfOrNull { it.id } ?: 0L) + 1, 1L)
        val clean = items.mapNotNull { raw ->
            val name = raw.name.trim().take(MAX_NAME)
            if (name.isEmpty()) return@mapNotNull null
            val id = if (raw.id > 0 && seen.add(raw.id)) raw.id else next++.also { seen.add(it) }
            sanitizeItem(raw.copy(id = id, name = name))
        }
        return Inventory(clean, settings.sanitized(), next)
    }
}

/** Normalises an article: bounded texts and numbers, unit-specific fields only where they apply. */
fun sanitizeItem(item: Item): Item {
    val unit = item.unit.trim().take(MAX_UNIT).ifEmpty { item.category.defaultUnit }
    val liters = item.litersPerUnit?.takeIf {
        item.category == Category.WATER && !unitKind(unit).isVolume && it.isFinite() && it > 0
    }?.coerceAtMost(MAX_LITERS_PER_UNIT)
    val kcal = item.kcalPerUnit?.takeIf { item.category == Category.FOOD && it.isFinite() && it >= 0 }?.coerceAtMost(MAX_KCAL)
    return item.copy(
        unit = unit,
        quantity = roundQuantity(item.quantity.finiteOr(0.0).coerceIn(0.0, MAX_QUANTITY)),
        location = item.location.trim().take(MAX_LOCATION),
        note = item.note.trim().take(MAX_NOTE),
        litersPerUnit = liters,
        kcalPerUnit = kcal,
    )
}

/** Rounds to 3 decimals, removing binary noise such as 0.1 + 0.2 = 0.30000000000000004. */
fun roundQuantity(value: Double): Double = Math.round(value * 1000.0) / 1000.0

internal fun Double.finiteOr(fallback: Double): Double = if (isFinite()) this else fallback

// ---------------------------------------------------------------- Units

enum class UnitKind(val isVolume: Boolean, val isMass: Boolean) {
    LITER(true, false), CENTILITER(true, false), MILLILITER(true, false),
    KILOGRAM(false, true), GRAM(false, true), OTHER(false, false)
}

fun unitKind(unit: String): UnitKind = when (normalizeForSearch(unit.trim()).trimEnd('.')) {
    "l", "litre", "litres", "liter", "liters" -> UnitKind.LITER
    "cl", "centilitre", "centilitres" -> UnitKind.CENTILITER
    "ml", "millilitre", "millilitres" -> UnitKind.MILLILITER
    "kg", "kgs", "kilo", "kilos", "kilogramme", "kilogrammes" -> UnitKind.KILOGRAM
    "g", "gr", "gramme", "grammes" -> UnitKind.GRAM
    else -> UnitKind.OTHER
}

private val SINGULARS = mapOf(
    "bocaux" to "bocal", "rouleaux" to "rouleau", "cadeaux" to "cadeau", "morceaux" to "morceau",
    "gâteaux" to "gâteau", "journaux" to "journal"
)

/** "boîtes" → "boîte", "bocaux" → "bocal", "L" → "L": the unit as it reads after "par". */
fun singularUnit(unit: String): String {
    val u = unit.trim()
    SINGULARS[u.lowercase()]?.let { return it }
    return if (u.length > 3 && u.endsWith("s") && unitKind(u) == UnitKind.OTHER) u.dropLast(1) else u
}

/**
 * The unit as it reads after [quantity]: French keeps the singular below 2
 * ("1 boîte", "0,5 litre", "1 bocal"), the plural from 2 ("3 boîtes"). Symbols are unchanged.
 */
fun unitForQuantity(quantity: Double, unit: String): String {
    val u = unit.trim()
    if (quantity >= 2.0) return u
    SINGULARS[u.lowercase()]?.let { return it }
    return if (u.length > 3 && u.endsWith("s") && !u.endsWith("ss")) u.dropLast(1) else u
}

/** Litres of drinking water held by [item], or null when it is not water or its volume is unknown. */
fun waterLiters(item: Item): Double? {
    if (item.category != Category.WATER) return null
    val q = item.quantity
    return when (unitKind(item.unit)) {
        UnitKind.LITER -> q
        UnitKind.CENTILITER -> q / 100.0
        UnitKind.MILLILITER -> q / 1000.0
        else -> item.litersPerUnit?.let { q * it }
    }
}

/** True when the volume of one unit must be typed in (water counted in bottles, packs…). */
fun needsLitersPerUnit(category: Category, unit: String): Boolean =
    category == Category.WATER && !unitKind(unit).isVolume

enum class KcalBasis { PER_100G, PER_100ML, PER_UNIT }

fun kcalBasis(unit: String): KcalBasis {
    val kind = unitKind(unit)
    return when {
        kind.isMass -> KcalBasis.PER_100G
        kind.isVolume -> KcalBasis.PER_100ML
        else -> KcalBasis.PER_UNIT
    }
}

/** Label of the calorie field, matching what nutrition tables print. */
fun kcalFieldLabel(unit: String): String = when (kcalBasis(unit)) {
    KcalBasis.PER_100G -> "kcal pour 100 g"
    KcalBasis.PER_100ML -> "kcal pour 100 mL"
    KcalBasis.PER_UNIT -> "kcal par ${singularUnit(unit.ifBlank { "unité" })}"
}

/** Total energy held by [item] in kcal, or null when it is not food or has no calorie value. */
fun itemKcal(item: Item): Double? {
    if (item.category != Category.FOOD) return null
    val k = item.kcalPerUnit ?: return null
    val q = item.quantity
    return when (unitKind(item.unit)) {
        UnitKind.KILOGRAM -> q * 1000.0 / 100.0 * k
        UnitKind.GRAM -> q / 100.0 * k
        UnitKind.LITER -> q * 1000.0 / 100.0 * k
        UnitKind.CENTILITER -> q * 10.0 / 100.0 * k
        UnitKind.MILLILITER -> q / 100.0 * k
        UnitKind.OTHER -> q * k
    }
}

/** When the category changes, a suggested (or empty) unit follows it; a unit typed by hand is kept. */
fun unitAfterCategoryChange(old: Category, new: Category, unit: String): String {
    val u = unit.trim()
    return if (u.isEmpty() || u == old.defaultUnit || (u in old.units && u !in new.units)) new.defaultUnit else u
}

// ---------------------------------------------------------------- Expiry status

enum class Status(val badge: String, val csv: String, val rank: Int) {
    EXPIRED("Périmé", "Périmé", 0),
    SOON("Bientôt", "Bientôt périmé", 1),
    OK("OK", "OK", 2),
    NO_DATE("Sans date", "Sans date", 3),
    EMPTY("Épuisé", "Épuisé", 4),
}

/** Days from [today] to the last valid day: 0 = expires today, negative = already expired. */
fun daysLeft(expiry: Expiry, today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiry.lastDay())

fun expiryStatus(expiry: Expiry, today: LocalDate, soonDays: Int): Status {
    val left = daysLeft(expiry, today)
    return when {
        left < 0 -> Status.EXPIRED
        left <= soonDays -> Status.SOON
        else -> Status.OK
    }
}

/** An exhausted article is no longer urgent, whatever its date. */
fun statusOf(item: Item, today: LocalDate, soonDays: Int): Status = when {
    item.quantity <= 0.0 -> Status.EMPTY
    item.expiry == null -> Status.NO_DATE
    else -> expiryStatus(item.expiry, today, soonDays)
}

/** "expire aujourd'hui", "expire dans 12 jours", "dans 5 mois", "périmé depuis hier"… */
fun relativeExpiry(expiry: Expiry, today: LocalDate): String {
    val last = expiry.lastDay()
    val d = ChronoUnit.DAYS.between(today, last)
    return when {
        d == 0L -> "expire aujourd'hui"
        d == 1L -> "expire demain"
        d == -1L -> "périmé depuis hier"
        d < 0 -> {
            val months = Period.between(last, today).toTotalMonths()
            if (months >= 2) "périmé depuis ${monthsOrYears(months)}" else "périmé depuis ${-d} jours"
        }
        else -> {
            val months = Period.between(today, last).toTotalMonths()
            if (months >= 2) "expire dans ${monthsOrYears(months)}" else "expire dans $d jours"
        }
    }
}

private fun monthsOrYears(months: Long): String = if (months >= 24) "${months / 12} ans" else "$months mois"

/** "15/03/2027 · expire dans 4 mois". */
fun expiryLine(expiry: Expiry, today: LocalDate): String = "${expiry.short()} · ${relativeExpiry(expiry, today)}"

// ---------------------------------------------------------------- Sorting, filtering, search

/** Expired first, then soon expired, then the rest by date, undated, and finally exhausted articles. */
fun sortByUrgency(items: List<Item>, today: LocalDate, soonDays: Int): List<Item> =
    items.sortedWith(
        compareBy<Item> { statusOf(it, today, soonDays).rank }
            .thenBy { it.expiry?.lastDay()?.toEpochDay() ?: Long.MAX_VALUE }
            .thenBy { normalizeForSearch(it.name) }
            .thenBy { it.id }
    )

data class ListFilter(val category: Category? = null, val alertsOnly: Boolean = false, val query: String = "") {
    val isActive: Boolean get() = category != null || alertsOnly || query.isNotBlank()
}

fun isAlert(status: Status): Boolean = status == Status.EXPIRED || status == Status.SOON

/** Every word of the query must appear (accents and case ignored) in the name, category, place or note. */
fun matchesQuery(item: Item, query: String): Boolean {
    val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = normalizeForSearch(listOf(item.name, item.category.label, item.location, item.note, item.unit).joinToString(" "))
    return words.all { haystack.contains(it) }
}

fun applyFilter(items: List<Item>, filter: ListFilter, today: LocalDate, soonDays: Int): List<Item> = items.filter {
    (filter.category == null || it.category == filter.category) &&
        (!filter.alertsOnly || isAlert(statusOf(it, today, soonDays))) &&
        matchesQuery(it, filter.query)
}

// ---------------------------------------------------------------- Household autonomy

data class Autonomy(
    val waterLiters: Double,
    val waterDays: Double,
    /** Water articles in bottles or packs whose volume is unknown. */
    val waterWithoutVolume: Int,
    val foodKcal: Double,
    /** Null when no (non-expired) food article has a calorie value. */
    val foodDays: Double?,
    val foodItems: Int,
    val foodWithoutKcal: Int,
    /** Water or food articles left out because they are past their date. */
    val expiredExcluded: Int,
    val expiredCount: Int,
    val soonCount: Int,
) {
    fun waterProgress(target: Int): Float = progress(waterDays, target)
    fun foodProgress(target: Int): Float = progress(foodDays ?: 0.0, target)

    private fun progress(days: Double, target: Int): Float =
        if (target <= 0) 1f else (days / target).coerceIn(0.0, 1.0).toFloat()
}

fun computeAutonomy(items: List<Item>, settings: Settings, today: LocalDate): Autonomy {
    var liters = 0.0
    var withoutVolume = 0
    var kcal = 0.0
    var foodItems = 0
    var withKcal = 0
    var withoutKcal = 0
    var excluded = 0
    var expired = 0
    var soon = 0
    for (item in items) {
        val status = statusOf(item, today, settings.soonDays)
        if (status == Status.EXPIRED) expired++
        if (status == Status.SOON) soon++
        val isExpired = item.expiry != null && daysLeft(item.expiry, today) < 0
        when (item.category) {
            Category.WATER -> when {
                isExpired -> if (item.quantity > 0) excluded++
                else -> {
                    val l = waterLiters(item)
                    if (l != null) liters += l else if (item.quantity > 0) withoutVolume++
                }
            }
            Category.FOOD -> {
                foodItems++
                when {
                    isExpired -> if (item.quantity > 0) excluded++
                    else -> {
                        val k = itemKcal(item)
                        if (k != null) {
                            kcal += k
                            withKcal++
                        } else if (item.quantity > 0) {
                            withoutKcal++
                        }
                    }
                }
            }
            else -> Unit
        }
    }
    val s = settings.sanitized()
    val waterNeed = s.persons * s.waterPerDay
    val kcalNeed = s.persons * s.kcalPerDay
    return Autonomy(
        waterLiters = liters,
        waterDays = liters / waterNeed,
        waterWithoutVolume = withoutVolume,
        foodKcal = kcal,
        foodDays = if (withKcal > 0) kcal / kcalNeed else null,
        foodItems = foodItems,
        foodWithoutKcal = withoutKcal,
        expiredExcluded = excluded,
        expiredCount = expired,
        soonCount = soon,
    )
}

// ---------------------------------------------------------------- Import

data class MergeResult(val inventory: Inventory, val added: Int, val skipped: Int)

/** Adds the imported articles that are not already present (same content), with fresh ids. */
fun mergeInto(current: Inventory, imported: List<Item>): MergeResult {
    val known = current.items.mapTo(HashSet()) { it.contentKey() }
    var next = current.nextId
    var skipped = 0
    val added = ArrayList<Item>()
    for (item in imported) {
        if (known.add(item.contentKey())) added += item.copy(id = next++) else skipped++
    }
    return MergeResult(current.copy(items = current.items + added, nextId = next), added.size, skipped)
}

/** The imported inventory, renumbered from 1, replaces everything (articles and settings). */
fun replaceWith(imported: Inventory): Inventory {
    val items = imported.items.mapIndexed { i, item -> item.copy(id = i + 1L) }
    return Inventory(items, imported.settings.sanitized(), items.size + 1L)
}

// ---------------------------------------------------------------- Editor draft

data class Draft(
    val name: String,
    val category: Category,
    val quantity: String,
    val unit: String,
    val expiry: Expiry?,
    val location: String,
    val note: String,
    val litersPerUnit: String,
    val kcal: String,
)

data class DraftCheck(
    val item: Item?,
    val nameError: String? = null,
    val quantityError: String? = null,
    val litersError: String? = null,
    val kcalError: String? = null,
)

fun draftOf(item: Item): Draft = Draft(
    name = item.name,
    category = item.category,
    quantity = formatDecimal(item.quantity, 3, grouping = false),
    unit = item.unit,
    expiry = item.expiry,
    location = item.location,
    note = item.note,
    litersPerUnit = item.litersPerUnit?.let { formatDecimal(it, 3, grouping = false) }.orEmpty(),
    kcal = item.kcalPerUnit?.let { formatDecimal(it, 1, grouping = false) }.orEmpty(),
)

fun newDraft(category: Category): Draft =
    Draft("", category, "1", category.defaultUnit, null, "", "", "", "")

/** Validates the editor fields; [DraftCheck.item] is null as long as one of them is wrong. */
fun checkDraft(draft: Draft, id: Long): DraftCheck {
    val name = draft.name.trim()
    val nameError = if (name.isEmpty()) "Indiquez un nom." else null

    val qText = draft.quantity.trim()
    val q = parseDecimal(qText)
    val quantityError = when {
        qText.isEmpty() -> "Indiquez une quantité."
        q == null -> "Nombre invalide (ex. 2 ou 1,5)."
        q > MAX_QUANTITY -> "Quantité trop grande."
        else -> null
    }

    val unit = draft.unit.trim().ifEmpty { draft.category.defaultUnit }
    var liters: Double? = null
    var litersError: String? = null
    if (needsLitersPerUnit(draft.category, unit) && draft.litersPerUnit.isNotBlank()) {
        val l = parseDecimal(draft.litersPerUnit)
        litersError = when {
            l == null -> "Contenance invalide (ex. 1,5)."
            l <= 0.0 -> "La contenance doit être supérieure à 0."
            l > MAX_LITERS_PER_UNIT -> "Contenance trop grande."
            else -> null
        }
        liters = l
    }

    var kcal: Double? = null
    var kcalError: String? = null
    if (draft.category == Category.FOOD && draft.kcal.isNotBlank()) {
        val k = parseDecimal(draft.kcal)
        kcalError = when {
            k == null -> "Valeur invalide (ex. 350)."
            k > MAX_KCAL -> "Valeur trop grande."
            else -> null
        }
        kcal = k
    }

    val ok = nameError == null && quantityError == null && litersError == null && kcalError == null
    val item = if (!ok) null else sanitizeItem(
        Item(
            id = id,
            name = name.take(MAX_NAME),
            category = draft.category,
            quantity = q ?: 0.0,
            unit = unit,
            expiry = draft.expiry,
            location = draft.location,
            note = draft.note,
            litersPerUnit = liters,
            kcalPerUnit = kcal,
        )
    )
    return DraftCheck(item, nameError, quantityError, litersError, kcalError)
}
