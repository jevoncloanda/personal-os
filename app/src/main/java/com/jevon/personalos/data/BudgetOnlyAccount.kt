package com.jevon.personalos.data

internal const val BUDGET_ACCOUNT_PREFIX = "poros:budget:"
internal const val BUDGET_ACCOUNT_MARKER = "Poros budget-only bookkeeping"
internal const val BUDGET_ONLY_LABEL = "Budget only"

internal fun budgetAccountId(currency: String): String = "$BUDGET_ACCOUNT_PREFIX$currency"

internal fun isBudgetAccountId(id: String?, currency: String): Boolean =
    id == budgetAccountId(currency)

internal val AccountEntity.isBudgetOnly: Boolean
    get() = notes == BUDGET_ACCOUNT_MARKER && isBudgetAccountId(id, currencyCode)
