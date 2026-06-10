package com.eventmap.search.ui

import com.eventmap.search.ui.viewmodel.SearchViewModel
import org.koin.dsl.module

val searchModule = module {
    factory { SearchViewModel(repository = get()) }
}
