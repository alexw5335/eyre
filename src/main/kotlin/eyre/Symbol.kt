package eyre



class SymBase(
	val scope     : ScopeIntern,
	val name      : StringIntern,
	val thisScope : ScopeIntern = scope,
	var resolved  : Boolean = true,
	var resolving : Boolean = false,
	var section   : Section = Section.NONE,
	var pos       : Int = 0
) {
	companion object {
		val EMPTY = SymBase(ScopeInterner.EMPTY, StringInterner.EMPTY)
	}
}



interface Symbol {
	val base: SymBase
	val scope get() = base.scope
	val name get() = base.name
	var resolved get() = base.resolved; set(v) { base.resolved = v }
	var resolving get() = base.resolving; set(v) { base.resolving = v }
}



interface ScopedSymbol : Symbol {
	val thisScope get() = base.thisScope
}



interface PosSymbol : Symbol {
	var section get() = base.section; set(v) { base.section = v }
	var pos get() = base.pos; set(v) { base.pos = v }
}


interface IntSymbol : Symbol {
	var intValue: Long
}

fun IntSymbol(base: SymBase, intValue: Long): IntSymbol {
	class IntSymbolImpl(override val base: SymBase, override var intValue: Long) : IntSymbol
	return IntSymbolImpl(base, intValue)
}



class Namespace(override val base: SymBase) : ScopedSymbol

class LabelSymbol(override val base: SymBase) : PosSymbol

class DllImportSymbol(override val base: SymBase) : PosSymbol

class DllSymbol(override val base: SymBase, val imports: MutableList<DllImportSymbol>) : Symbol

class VarSymbol(override val base: SymBase, val size: Int) : PosSymbol

class ResSymbol(override val base: SymBase, var size: Int = 0) : PosSymbol

class ConstSymbol(override val base: SymBase, override var intValue: Long = 0L): IntSymbol {
	lateinit var node: ConstNode
}

class EnumEntrySymbol(
	override var base     : SymBase,
	val ordinal           : Int,
	override var intValue : Long = 0L
) : IntSymbol {
	lateinit var parent: EnumSymbol
	lateinit var node: EnumEntryNode
}

class EnumSymbol(
	override val base : SymBase,
	val entries       : List<EnumEntrySymbol>
) : ScopedSymbol {
	lateinit var node: EnumNode
}