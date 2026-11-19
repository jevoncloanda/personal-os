package com.jevon.personalos.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.jevon.personalos.today.TodayViewModel

@Composable
internal fun DeveloperPreviewAction(model: TodayViewModel) = Unit

@Composable
internal fun DeveloperPreviewBanner(model: TodayViewModel) = Unit

internal fun LazyListScope.developerMoneyTools(section: String, selectSection: (String) -> Unit) = Unit
