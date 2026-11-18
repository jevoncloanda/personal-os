package com.jevon.personalos.widget

internal data class QuickExpenseLayout(
    val actionSlots: Int,
    val columns: Int,
    val showHeader: Boolean,
    val compact: Boolean,
) {
    val presetCapacity: Int get() = actionSlots - 1
}

/** Capacity is measured from usable bounds, independently of a launcher's cell grid. */
internal fun quickExpenseLayout(width: Float, height: Float, fontScale: Float = 1f): QuickExpenseLayout {
    val textScale = fontScale.coerceAtLeast(1f)
    val availableWidth = width / textScale
    val availableHeight = height / textScale
    val layout = when {
        // Header + two rows of readable, >=48dp action targets.
        availableWidth >= 280 && availableHeight >= 184 ->
            QuickExpenseLayout(4, 2, showHeader = true, compact = false)
        // A tall, narrow instance can fit the same actions in one column.
        availableWidth >= 140 && availableHeight >= 320 ->
            QuickExpenseLayout(4, 1, showHeader = availableWidth >= 180, compact = false)
        availableWidth >= 280 && availableHeight >= 80 ->
            QuickExpenseLayout(2, 2, showHeader = availableHeight >= 136, compact = true)
        availableHeight >= 144 ->
            QuickExpenseLayout(2, 1,
                showHeader = availableWidth >= 180 && availableHeight >= 208, compact = true)
        else -> QuickExpenseLayout(1, 1, showHeader = false, compact = true)
    }
    val rows = layout.actionSlots / layout.columns
    val tileHeight = (availableHeight - 16 - if (layout.showHeader) 48 else 0) / rows
    return layout.copy(compact = tileHeight < 88 || availableWidth / layout.columns < 140)
}
