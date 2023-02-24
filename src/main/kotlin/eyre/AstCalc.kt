package eyre

fun calcSymbolInt(symbol: Symbol): Long = when(symbol) {
	is IntSymbol -> symbol.value
	is ConstIntSymbol -> symbol.value
	is EnumEntrySymbol -> symbol.value
	else -> error("Invalid symbol: $symbol")
}