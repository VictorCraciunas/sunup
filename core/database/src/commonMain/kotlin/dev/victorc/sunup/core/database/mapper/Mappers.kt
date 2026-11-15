package dev.victorc.sunup.core.database.mapper

import dev.victorc.sunup.core.database.entity.AlarmEntity
import dev.victorc.sunup.core.database.entity.DeckEntity
import dev.victorc.sunup.core.database.entity.NoteEntity
import dev.victorc.sunup.core.database.entity.ReviewLogEntity
import dev.victorc.sunup.core.database.entity.ReviewStateEntity
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.MasteryLevel
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteType
import dev.victorc.sunup.core.domain.learning.ReviewContext
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewState
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// Enum values are stored by name. Unknown names (e.g. written by a newer app version) fall
// back to a safe default instead of crashing.

// Decks

fun Deck.toEntity(createdAt: Long, updatedAt: Long) = DeckEntity(
    id = id,
    name = name,
    kind = kind.name,
    targetLanguage = targetLanguage,
    nativeLanguage = nativeLanguage,
    bothDirections = bothDirections,
    newCardsPerDay = newCardsPerDay,
    allowedGames = allowedGames.joinToString(",") { it.name },
    examEpochDay = examDate?.toEpochDays(),
    builtInPackId = builtInPackId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DeckEntity.toDomain() = Deck(
    id = id,
    name = name,
    kind = enumOrDefault(kind, DeckKind.STUDY),
    targetLanguage = targetLanguage,
    nativeLanguage = nativeLanguage,
    bothDirections = bothDirections,
    newCardsPerDay = newCardsPerDay,
    allowedGames = allowedGames.split(',').mapNotNull { name -> Game.entries.firstOrNull { it.name == name } }.toSet(),
    examDate = examEpochDay?.let { LocalDate.fromEpochDays(it) },
    builtInPackId = builtInPackId,
)

// Notes

fun Note.toEntity(createdAt: Long, updatedAt: Long) = NoteEntity(
    id = id,
    deckId = deckId,
    type = type.name,
    front = front,
    back = back,
    acceptedAnswers = encodeList(acceptedAnswers),
    example = example,
    exampleTranslation = exampleTranslation,
    hint = hint,
    explanation = explanation,
    wrongAnswers = encodeList(wrongAnswers),
    game = game.name,
    isTrue = isTrue,
    pairs = encodePairs(pairs),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NoteEntity.toDomain() = Note(
    id = id,
    deckId = deckId,
    type = enumOrDefault(type, NoteType.QUESTION),
    front = front,
    back = back,
    acceptedAnswers = decodeList(acceptedAnswers),
    example = example,
    exampleTranslation = exampleTranslation,
    hint = hint,
    explanation = explanation,
    wrongAnswers = decodeList(wrongAnswers),
    game = enumOrDefault(game, Game.PICK_MEANING),
    isTrue = isTrue,
    pairs = decodePairs(pairs),
)

// Review state and log

fun ReviewState.toEntity(updatedAt: Long) = ReviewStateEntity(
    noteId = noteId,
    direction = direction.name,
    step = step,
    level = level.name,
    dueEpochDay = due?.toEpochDays(),
    reviews = reviews,
    lapses = lapses,
    updatedAt = updatedAt,
)

fun ReviewStateEntity.toDomain() = ReviewState(
    noteId = noteId,
    direction = enumOrDefault(direction, Direction.FORWARD),
    step = step,
    level = enumOrDefault(level, MasteryLevel.RECOGNIZE),
    due = dueEpochDay?.let { LocalDate.fromEpochDays(it) },
    reviews = reviews,
    lapses = lapses,
)

fun ReviewRecord.toEntity() = ReviewLogEntity(
    id = id,
    noteId = noteId,
    direction = direction.name,
    game = game.name,
    grade = grade.name,
    context = context.name,
    reviewedAt = reviewedAt.toEpochMilliseconds(),
    durationMillis = duration?.inWholeMilliseconds,
    updatedAt = reviewedAt.toEpochMilliseconds(),
)

fun ReviewLogEntity.toDomain() = ReviewRecord(
    id = id,
    noteId = noteId,
    direction = enumOrDefault(direction, Direction.FORWARD),
    game = enumOrDefault(game, Game.PICK_MEANING),
    grade = enumOrDefault(grade, Grade.GOOD),
    context = enumOrDefault(context, ReviewContext.PRACTICE),
    reviewedAt = Instant.fromEpochMilliseconds(reviewedAt),
    duration = durationMillis?.milliseconds,
)

// Alarms

fun WakeAlarm.toEntity(createdAt: Long, updatedAt: Long) = AlarmEntity(
    id = id,
    minuteOfDay = time.hour * 60 + time.minute,
    repeatDays = repeatDays.fold(0) { mask, day -> mask or (1 shl (day.isoDayNumber - 1)) },
    enabled = enabled,
    label = label,
    deckId = deckId,
    mode = mode.name,
    preAlarm = preAlarm,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AlarmEntity.toDomain() = WakeAlarm(
    id = id,
    time = LocalTime(hour = minuteOfDay / 60, minute = minuteOfDay % 60),
    repeatDays = (1..7).filter { repeatDays and (1 shl (it - 1)) != 0 }.map { DayOfWeek(it) }.toSet(),
    enabled = enabled,
    label = label,
    deckId = deckId,
    mode = enumOrDefault(mode, WakeMode.NORMAL),
    preAlarm = preAlarm,
)

// Helpers

private val json = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())

private fun encodeList(values: List<String>): String = json.encodeToString(stringList, values)

private fun decodeList(text: String): List<String> =
    runCatching { json.decodeFromString(stringList, text) }.getOrDefault(emptyList())

private val pairList = ListSerializer(ListSerializer(String.serializer()))

private fun encodePairs(values: List<Pair<String, String>>): String =
    json.encodeToString(pairList, values.map { listOf(it.first, it.second) })

private fun decodePairs(text: String): List<Pair<String, String>> =
    runCatching { json.decodeFromString(pairList, text) }
        .getOrDefault(emptyList())
        .filter { it.size == 2 }
        .map { it[0] to it[1] }

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
