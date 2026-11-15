package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupFieldLabel
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupTextField
import dev.victorc.sunup.core.designsystem.components.SunupTopBar
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.feature.decks.domain.CardDraft
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Mockups E1–E5: the form for one card in [game]. [cardId] null makes a new card. */
@Composable
fun CardEditorRoot(deckId: String, game: Game, cardId: String?, onChangeGame: () -> Unit, onClose: () -> Unit) {
    val viewModel = koinViewModel<CardEditorViewModel>(key = "card-$deckId-$game-${cardId ?: "new"}") {
        parametersOf(deckId, game, cardId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CardEditorEvent.Close -> close()
            }
        }
    }
    CardEditorScreen(state, viewModel::onAction, onChangeGame = onChangeGame, onBack = onClose)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun CardEditorScreen(
    state: CardEditorState,
    onAction: (CardEditorAction) -> Unit,
    onChangeGame: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val draft = state.draft
    fun edit(change: CardDraft.() -> CardDraft) = onAction(CardEditorAction.OnDraft(draft.change()))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SunupTopBar(
            title = if (state.isNew) "Add a card" else "Edit card",
            onBack = onBack,
            actions = {
                if (state.isNew) {
                    Text("${state.cardsInDeck} in this deck", style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
                }
            },
        )
        ChosenGame(draft.game, canChange = state.isNew, onChange = onChangeGame)

        when (draft.game) {
            Game.TRUE_FALSE -> TrueFalseForm(draft, ::edit)
            Game.TYPE_IT -> TypeItForm(draft, ::edit)
            Game.FILL_THE_GAP -> GapForm(draft, ::edit)
            Game.MATCH_PAIRS -> MatchForm(draft, ::edit)
            else -> PickForm(draft, ::edit)
        }

        state.problem?.let {
            Text(
                it,
                style = SunupTheme.typography.bodySmall,
                color = colors.wrong,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.saved) {
            Text(
                "✓  Card added. Next one!",
                style = SunupTheme.typography.body,
                color = colors.onCorrectContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.correctContainer, SunupTheme.shapes.card)
                    .padding(spacing.md)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Spacer(Modifier.heightIn(min = spacing.sm))
        SunupButton(
            text = if (state.isNew) "Save & add another" else "Save",
            onClick = { onAction(CardEditorAction.OnSave) },
            enabled = !state.saving,
        )
        if (state.isNew) {
            TextButton(onClick = { onAction(CardEditorAction.OnDone) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Done", color = colors.indigo)
            }
        } else {
            TextButton(onClick = { onAction(CardEditorAction.OnDelete) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Delete card", color = colors.wrong)
            }
        }
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onAction(CardEditorAction.OnCancelDelete) },
            title = { Text("Delete this card?") },
            text = { Text("It won't come up in the morning any more. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onAction(CardEditorAction.OnConfirmDelete) }) { Text("Delete", color = colors.wrong) }
            },
            dismissButton = { TextButton(onClick = { onAction(CardEditorAction.OnCancelDelete) }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun ChosenGame(game: Game, canChange: Boolean, onChange: () -> Unit) {
    val colors = SunupTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.indigoContainer, SunupTheme.shapes.card)
            .padding(horizontal = SunupTheme.spacing.md, vertical = SunupTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        GameBadge(game, size = SunupTheme.sizes.pipBadge)
        Column(Modifier.weight(1f)) {
            Text(game.title(), style = SunupTheme.typography.body, color = colors.ink)
            Text(game.example(), style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
        }
        if (canChange) TextButton(onClick = onChange) { Text("Change", color = colors.indigo) }
    }
}

private typealias Edit = (CardDraft.() -> CardDraft) -> Unit

@Composable
private fun PickForm(draft: CardDraft, edit: Edit) {
    SunupTextField(draft.question, { v -> edit { copy(question = v) } }, label = "Question", placeholder = "e.g. Who painted the Mona Lisa?")
    SunupTextField(draft.answer, { v -> edit { copy(answer = v) } }, label = "Right answer", placeholder = "e.g. Leonardo da Vinci", accent = SunupTheme.colors.correct)
    SunupFieldLabel("Wrong answers (optional)")
    draft.wrong.forEachIndexed { index, value ->
        SunupTextField(
            value = value,
            onValueChange = { v -> edit { copy(wrong = wrong.toMutableList().also { it[index] = v }) } },
            label = null,
            placeholder = if (index == 0) "e.g. Michelangelo" else "Wrong answer ${index + 1}",
        )
    }
    Hint("Leave them empty and Pip uses answers from your other cards.")
}

@Composable
private fun TrueFalseForm(draft: CardDraft, edit: Edit) {
    SunupTextField(draft.question, { v -> edit { copy(question = v) } }, label = "Statement", placeholder = "e.g. The Eiffel Tower is in Paris.", singleLine = false)
    SunupFieldLabel("Is it true?")
    Row(horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
        Choice("True", on = draft.isTrue, onClick = { edit { copy(isTrue = true) } }, modifier = Modifier.weight(1f))
        Choice("False", on = !draft.isTrue, onClick = { edit { copy(isTrue = false) } }, modifier = Modifier.weight(1f))
    }
    SunupTextField(draft.why, { v -> edit { copy(why = v) } }, label = "Why (optional)", placeholder = "e.g. It was built for the 1889 World's Fair.")
    Hint("Shown after you answer, so the fact sticks.")
}

@Composable
private fun TypeItForm(draft: CardDraft, edit: Edit) {
    SunupTextField(draft.question, { v -> edit { copy(question = v) } }, label = "Question", placeholder = "e.g. In which year did World War II end?")
    SunupTextField(draft.answer, { v -> edit { copy(answer = v) } }, label = "Answer", placeholder = "e.g. 1945", accent = SunupTheme.colors.correct)
    SunupTextField(draft.alsoRight, { v -> edit { copy(alsoRight = v) } }, label = "Also counts as right (optional)", placeholder = "e.g. nineteen forty-five (separate with commas)")
    Hint("Capitals, small typos and missing accents still count.")
}

@Composable
private fun GapForm(draft: CardDraft, edit: Edit) {
    SunupTextField(
        draft.question,
        { v -> edit { copy(question = v) } },
        label = "Sentence",
        placeholder = "e.g. The [Romans] built Hadrian's Wall.",
        singleLine = false,
    )
    Hint("Put the missing word in [brackets]. That word becomes the gap.")
    SunupTextField(draft.translation, { v -> edit { copy(translation = v) } }, label = "Extra line under the sentence (optional)", placeholder = "e.g. A translation, or a hint")
    SunupTextField(draft.wrongList, { v -> edit { copy(wrongList = v) } }, label = "Wrong answers (optional)", placeholder = "e.g. Vikings, Normans, Celts")
    Hint("Empty: Pip uses words from your other cards.")
}

@Composable
private fun MatchForm(draft: CardDraft, edit: Edit) {
    SunupFieldLabel("4 pairs: left matches right")
    draft.pairs.forEachIndexed { index, (left, right) ->
        Row(horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            SunupTextField(
                value = left,
                onValueChange = { v -> edit { copy(pairs = pairs.toMutableList().also { it[index] = v to it[index].second }) } },
                label = null,
                placeholder = if (index == 0) "e.g. France" else "Left ${index + 1}",
                modifier = Modifier.weight(1f),
            )
            SunupTextField(
                value = right,
                onValueChange = { v -> edit { copy(pairs = pairs.toMutableList().also { it[index] = it[index].first to v }) } },
                label = null,
                placeholder = if (index == 0) "e.g. Paris" else "Right ${index + 1}",
                modifier = Modifier.weight(1f),
            )
        }
    }
    Hint("At least 2 pairs. The right side gets shuffled.")
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (on) colors.indigo else colors.surface,
        edge = if (on) colors.indigoShadow else colors.border,
        outline = if (on) null else colors.border,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        role = Role.RadioButton,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(SunupTheme.spacing.md),
        modifier = modifier
            .wobbleWhenOn(on)
            .semantics { selected = on },
    ) {
        Text(text, style = SunupTheme.typography.body, color = if (on) colors.onIndigo else colors.inkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = SunupTheme.typography.bodySmall, color = SunupTheme.colors.inkMuted)
}
