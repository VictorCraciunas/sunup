package dev.victorc.sunup.feature.decks.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Needs the repositories and GeoDeckSeeder (dataModule), CardBuilder and GeoAtlasSource
 * (wakeUpModule).
 */
val decksModule = module {
    viewModel { DeckListViewModel(decks = get(), notes = get(), alarms = get(), atlasSource = get(), geoSeeder = get()) }
    viewModel { NewDeckViewModel(decks = get()) }
    viewModel { ImportViewModel(decks = get(), notes = get()) }
    viewModel { params ->
        DeckDetailViewModel(deckId = params.get(0), decks = get(), notes = get(), alarms = get(), cards = get())
    }
    viewModel { params ->
        CardEditorViewModel(
            deckId = params.get(0),
            game = params.get(1),
            // Positional: deckId and cardId are both strings, and cardId can be null.
            cardId = params.values.getOrNull(2) as String?,
            decks = get(),
            notes = get(),
        )
    }
}
