package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.feature.decks.domain.CardProblem
import kotlinx.datetime.DayOfWeek

// TODO(M4): move user-facing text to string resources once localization is set up.

internal fun Game.title(): String = when (this) {
    Game.PICK_MEANING -> "Pick from 4"
    Game.TRUE_FALSE -> "True or false"
    Game.TYPE_IT -> "Type it"
    Game.FILL_THE_GAP -> "Fill the gap"
    Game.MATCH_PAIRS -> "Match pairs"
    Game.FIND_ON_MAP -> "Find on the map"
    Game.NAME_ON_MAP -> "Name the country"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** The one-line example on the game picker (mockup D4). */
internal fun Game.example(): String = when (this) {
    Game.PICK_MEANING -> "Who painted the Mona Lisa? → tap “Leonardo da Vinci”"
    Game.TRUE_FALSE -> "“The Eiffel Tower is in Paris.” → True"
    Game.TYPE_IT -> "When did World War II end? → type “1945”"
    Game.FILL_THE_GAP -> "The ___ built Hadrian's Wall."
    Game.MATCH_PAIRS -> "4 countries ↔ 4 capitals"
    else -> ""
}

internal fun Game.badge(): String = when (this) {
    Game.PICK_MEANING -> "1/4"
    Game.TRUE_FALSE -> "T/F"
    Game.TYPE_IT -> "Aa"
    Game.FILL_THE_GAP -> "_ _"
    Game.MATCH_PAIRS -> "⇄"
    Game.FIND_ON_MAP, Game.NAME_ON_MAP -> "◎"
    else -> "?"
}

/** Background and text colour of a game's badge. */
@Composable
internal fun Game.badgeColors(): Pair<Color, Color> = with(SunupTheme.colors) {
    when (this@badgeColors) {
        Game.PICK_MEANING -> daySky to indigo
        Game.TRUE_FALSE -> sunSoft to onSunSoft
        Game.TYPE_IT -> indigoContainer to indigoSoft
        Game.FILL_THE_GAP -> correctContainer to onCorrectContainer
        Game.MATCH_PAIRS -> wrongContainer to onWrongContainer
        else -> track to inkMuted
    }
}

@Composable
internal fun GameBadge(game: Game, size: Dp = SunupTheme.sizes.minTouchTarget) {
    val (background, content) = game.badgeColors()
    Box(
        modifier = Modifier
            .size(size)
            .background(background, SunupTheme.shapes.button)
            .semantics { contentDescription = game.title() },
        contentAlignment = Alignment.Center,
    ) {
        Text(game.badge(), style = SunupTheme.typography.label, color = content)
    }
}

internal fun DeckKind.title(): String = when (this) {
    DeckKind.LANGUAGE -> "Language"
    DeckKind.STUDY -> "Study"
    DeckKind.TRIVIA -> "Trivia"
    DeckKind.GEOGRAPHY -> "Map"
}

internal fun DeckKind.description(): String = when (this) {
    DeckKind.LANGUAGE -> "Words and their meanings"
    DeckKind.STUDY -> "Questions and answers for a course or exam"
    DeckKind.TRIVIA -> "Fun facts, just for the joy of it"
    DeckKind.GEOGRAPHY -> "Find countries on the map"
}

internal fun DeckKind.badge(): String = when (this) {
    DeckKind.LANGUAGE -> "ABC"
    DeckKind.STUDY -> "?!"
    DeckKind.TRIVIA -> "FUN"
    DeckKind.GEOGRAPHY -> "◎"
}

/** Two lines for a card in the deck list: what it asks, and its answer. */
internal fun Note.summary(): Pair<String, String> = when (game) {
    Game.TRUE_FALSE -> front to if (isTrue == false) "False" else "True"
    Game.FILL_THE_GAP -> front to exampleTranslation.orEmpty()
    Game.MATCH_PAIRS -> pairs.joinToString(" · ") { it.first } to "${pairs.size} pairs"
    else -> front to back
}

internal fun CardProblem.message(): String = when (this) {
    CardProblem.NO_QUESTION -> "Write the question first."
    CardProblem.NO_ANSWER -> "Write the right answer."
    CardProblem.NO_STATEMENT -> "Write the statement first."
    CardProblem.NO_SENTENCE -> "Write the sentence first."
    CardProblem.NO_BRACKETS -> "Put the missing word in [brackets], e.g. “The [Romans] built Hadrian's Wall.”"
    CardProblem.TOO_FEW_PAIRS -> "Fill in at least two pairs."
    CardProblem.SAME_WORD_TWICE -> "Each word can only be in one pair."
}

/** "07:00 Mon–Fri" for the alarms that use a deck. */
internal fun WakeAlarm.shortText(): String {
    val time = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    val week = DayOfWeek.entries
    val days = when {
        repeatDays.isEmpty() -> "once"
        repeatDays.size == 7 -> "every day"
        repeatDays == week.take(5).toSet() -> "Mon–Fri"
        repeatDays == week.drop(5).toSet() -> "Sat–Sun"
        else -> week.filter { it in repeatDays }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
    }
    return "$time $days"
}
