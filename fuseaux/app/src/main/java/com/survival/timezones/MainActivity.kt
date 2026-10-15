package com.survival.timezones

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val Bg = Color(0xFF1A1A2E)
private val Card = Color(0xFF16213E)
private val Navy = Color(0xFF0F3460)
private val Accent = Color(0xFF4A9FFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Warning = Color(0xFFFFD700)
private val Danger = Color(0xFFFF6B6B)

private val Tnum = TextStyle(fontFeatureSettings = "tnum")

private val TimeWithSeconds: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE)
private val TimeHoursMinutes: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
private val LongDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
private val ShortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.FRANCE)

private const val MINUS = "−"

// U+FE0E forces the text (monochrome) glyph so the sun is tinted like the moon instead of becoming an emoji.
private const val SUN = "☀︎"
private const val MOON = "☾"

// Declared before CITIES: top-level properties are initialised in source order.
private val Diacritics = Regex("\\p{Mn}+")

private fun normalizeForSearch(text: String): String =
    Diacritics.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        .lowercase(Locale.ROOT)
        .replace('-', ' ')
        .replace('_', ' ')

private class City(val name: String, val country: String, zoneId: String) {
    val zone: ZoneId = ZoneId.of(zoneId)
    val searchKey: String = normalizeForSearch("$name $country $zoneId")
}

private val CITIES = listOf(
    City("UTC", "Temps universel coordonné", "UTC"),
    City("Paris", "France", "Europe/Paris"),
    City("Londres", "Royaume-Uni", "Europe/London"),
    City("Reykjavik", "Islande", "Atlantic/Reykjavik"),
    City("Dakar", "Sénégal", "Africa/Dakar"),
    City("Le Caire", "Égypte", "Africa/Cairo"),
    City("Johannesburg", "Afrique du Sud", "Africa/Johannesburg"),
    City("Nairobi", "Kenya", "Africa/Nairobi"),
    City("Moscou", "Russie", "Europe/Moscow"),
    City("Dubaï", "Émirats arabes unis", "Asia/Dubai"),
    City("Mumbai", "Inde", "Asia/Kolkata"),
    City("Katmandou", "Népal", "Asia/Kathmandu"),
    City("Bangkok", "Thaïlande", "Asia/Bangkok"),
    City("Singapour", "Singapour", "Asia/Singapore"),
    City("Pékin", "Chine", "Asia/Shanghai"),
    City("Tokyo", "Japon", "Asia/Tokyo"),
    City("Sydney", "Australie", "Australia/Sydney"),
    City("Auckland", "Nouvelle-Zélande", "Pacific/Auckland"),
    City("Honolulu", "États-Unis (Hawaï)", "Pacific/Honolulu"),
    City("Los Angeles", "États-Unis", "America/Los_Angeles"),
    City("Denver", "États-Unis", "America/Denver"),
    City("Chicago", "États-Unis", "America/Chicago"),
    City("New York", "États-Unis", "America/New_York"),
    City("Montréal", "Canada", "America/Toronto"),
    City("Mexico", "Mexique", "America/Mexico_City"),
    City("São Paulo", "Brésil", "America/Sao_Paulo"),
    City("Buenos Aires", "Argentine", "America/Argentina/Buenos_Aires")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { SurvieTheme { AppScreen() } }
    }
}

@Composable
fun SurvieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Bg,
            primaryContainer = Navy, onPrimaryContainer = Color.White,
            secondary = Accent, onSecondary = Bg,
            secondaryContainer = Navy, onSecondaryContainer = Color.White,
            background = Bg, onBackground = Color.White,
            surface = Card, onSurface = Color.White,
            surfaceVariant = Navy, onSurfaceVariant = TextSecondary,
            surfaceContainer = Card, surfaceContainerHigh = Card, surfaceContainerHighest = Navy,
            outline = Navy, outlineVariant = Navy,
            error = Danger, onError = Bg
        ),
        content = content
    )
}

@Composable
private fun AppScreen() {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Re-reading the system zone on every tick follows a time-zone change while travelling.
                val tick = ZonedDateTime.now()
                now = tick
                // Sleep to the next wall-clock second of the instant just shown, so no second is skipped or repeated.
                delay(1_000L - tick.nano / 1_000_000)
            }
        }
    }
    // City rows only show minutes: they recompose once per minute instead of every second.
    val currentMinute by remember { derivedStateOf { now.truncatedTo(ChronoUnit.MINUTES) } }

    var query by rememberSaveable { mutableStateOf("") }
    val visibleCities = remember(query) {
        val needle = normalizeForSearch(query.trim())
        if (needle.isEmpty()) CITIES else CITIES.filter { needle in it.searchKey }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Fuseaux horaires", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("L'heure partout dans le monde, hors ligne", color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "local") { LocalTimeCard(now) }
            item(key = "search") { SearchField(query = query, onQueryChange = { query = it }) }
            if (visibleCities.isEmpty()) {
                item(key = "empty") { NoResult(query.trim()) }
            }
            items(visibleCities, key = { it.zone.id }) { city ->
                CityRow(city = city, now = currentMinute)
            }
        }
    }
}

@Composable
private fun LocalTimeCard(now: ZonedDateTime) {
    val date = now.format(LongDate).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("Heure locale", color = TextSecondary, fontSize = 14.sp)
        Text(
            now.format(TimeWithSeconds),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = Tnum
        )
        Text(date, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "${now.zone.id} · ${formatUtcOffset(now.offset.totalSeconds)}",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        placeholder = { Text("Ville ou pays", maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Card,
            unfocusedContainerColor = Card,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Navy
        )
    )
}

@Composable
private fun NoResult(query: String) {
    Text(
        "Aucune ville ne correspond à « $query ».",
        color = TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}

@Composable
private fun CityRow(city: City, now: ZonedDateTime) {
    val cityTime = now.withZoneSameInstant(city.zone)
    val isDay = cityTime.hour in 6 until 18
    val dayLabel = relativeDayLabel(now.toLocalDate(), cityTime)
    val offsetLabel = formatRelativeOffset(cityTime.offset.totalSeconds - now.offset.totalSeconds)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (isDay) SUN else MOON,
            color = if (isDay) Warning else TextSecondary,
            fontSize = 22.sp,
            modifier = Modifier
                .width(32.dp)
                .semantics { contentDescription = if (isDay) "Jour" else "Nuit" }
        )
        Column(Modifier.weight(1f)) {
            Text(
                city.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${city.country} · ${formatUtcOffset(cityTime.offset.totalSeconds)}",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                cityTime.format(TimeHoursMinutes),
                color = Accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = Tnum
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dayLabel != null) {
                    Text(dayLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" · ", color = TextSecondary, fontSize = 13.sp)
                }
                Text(offsetLabel, color = TextSecondary, fontSize = 13.sp, style = Tnum)
            }
        }
    }
}

private fun relativeDayLabel(localDate: LocalDate, cityTime: ZonedDateTime): String? =
    when (ChronoUnit.DAYS.between(localDate, cityTime.toLocalDate())) {
        0L -> null
        1L -> "Demain"
        -1L -> "Hier"
        // Only reachable from the most extreme device zones (e.g. UTC−12 vs UTC+13).
        else -> cityTime.format(ShortDay).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    }

/** "+6 h", "−5 h 30", "+5 h 45", "+30 min" or "même heure". */
private fun formatRelativeOffset(diffSeconds: Int): String {
    val totalMinutes = abs(diffSeconds) / 60
    if (totalMinutes == 0) return "même heure"
    val sign = if (diffSeconds > 0) "+" else MINUS
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        minutes == 0 -> "$sign$hours h"
        hours == 0 -> "$sign$minutes min"
        else -> String.format(Locale.FRANCE, "%s%d h %02d", sign, hours, minutes)
    }
}

/** "UTC+2", "UTC+5:45", "UTC−3", "UTC+0". */
private fun formatUtcOffset(totalSeconds: Int): String {
    val totalMinutes = abs(totalSeconds) / 60
    val sign = if (totalSeconds < 0) MINUS else "+"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0) {
        "UTC$sign$hours"
    } else {
        String.format(Locale.FRANCE, "UTC%s%d:%02d", sign, hours, minutes)
    }
}
