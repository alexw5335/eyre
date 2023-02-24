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

interface ConstSymbol : Symbol {
	var resolved: Boolean
	var resolving: Boolean
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
	override val scope : ScopeIntern,
	override val name  : StringIntern,
	val imports        : ArrayList<DllImportSymbol>
) : Symbol

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

class IntSymbol(
	override val scope : ScopeIntern,
	override val name  : StringIntern,
	var value          : Long
) : Symbol

class ConstIntSymbol(
	override val scope    : ScopeIntern,
	override val name     : StringIntern,
	override var resolved : Boolean = false,
	var value             : Long = 0L
) : ConstSymbol {
	lateinit var node: ConstNode
	override var resolving = false
}

class EnumEntrySymbol(
	override val scope: ScopeIntern,
	override val name: StringIntern,
	override var resolved: Boolean = false,
	var value: Long = 0L
) : ConstSymbol {
	lateinit var parent: EnumSymbol
	override var resolving = false
}

class EnumSymbol(
	override val scope     : ScopeIntern,
	override val name      : StringIntern,
	override val thisScope : ScopeIntern,
	val entries            : List<EnumEntrySymbol>
) : ScopedSymbol {
	lateinit var node: EnumNode
}

