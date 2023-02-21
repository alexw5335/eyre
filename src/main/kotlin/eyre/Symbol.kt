package eyre



interface Symbol {
	val scope: ScopeIntern
	val name: StringIntern
}

interface ScopedSymbol : Symbol {
	val thisScope: ScopeIntern
}

interface PosSymbol : Symbol {
	val section: Section
	val pos: Int
}

class Namespace(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override val thisScope: ScopeIntern
) : ScopedSymbol

class LabelSymbol(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override val section: Section,
	override val pos: Int
) : PosSymbol

class DllImportSymbol(
	override val scope   : ScopeIntern,
	override val name    : StringIntern,
	override val section : Section,
	override val pos     : Int
) : PosSymbol

class DllSymbol(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override val thisScope: ScopeIntern,
	val symbols: ArrayList<DllImportSymbol>
) : ScopedSymbol