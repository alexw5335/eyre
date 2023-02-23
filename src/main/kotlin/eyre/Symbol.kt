package eyre



interface Symbol {
	val scope: ScopeIntern
	val name: StringIntern
}

interface ScopedSymbol : Symbol {
	val thisScope: ScopeIntern
}

interface PosSymbol : Symbol {
	var section: Section
	var pos: Int
}

class Namespace(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override val thisScope: ScopeIntern
) : ScopedSymbol

class LabelSymbol(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override var section: Section,
	override var pos: Int
) : PosSymbol

class DllImportSymbol(
	override val scope   : ScopeIntern,
	override val name    : StringIntern,
	override var section : Section,
	override var pos     : Int
) : PosSymbol

class DllSymbol(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override val thisScope: ScopeIntern,
	val symbols: ArrayList<DllImportSymbol>
) : ScopedSymbol

class VarSymbol(
	override val scope   : ScopeIntern,
	override val name    : StringIntern,
	override var section : Section,
	override var pos     : Int,
	val size             : Int
) : PosSymbol

class ResSymbol(
	override val scope   : ScopeIntern,
	override val name    : StringIntern,
	override var section : Section,
	override var pos     : Int,
	var size             : Int
) : PosSymbol