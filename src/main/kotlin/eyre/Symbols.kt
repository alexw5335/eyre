package eyre



class SymBase(
	val srcPos    : SrcPos?,
	val scope     : ScopeIntern,
	val name      : StringIntern,
) {

	// Only used by compile-time constants that may be referenced by other constants
	var resolved = false

	companion object {
		val EMPTY = SymBase(null, ScopeInterner.EMPTY, StringInterner.EMPTY)
	}

}



interface Symbol {
	val base: SymBase
	val srcPos   get() = base.srcPos
	val scope    get() = base.scope
	val name     get() = base.name
	var resolved get() = base.resolved; set(v) { base.resolved = v }
}



interface ScopedSymbol : Symbol {
	val thisScope: ScopeIntern
}



interface PosSymbol : Symbol {
	var section: Section
	var pos: Int
}



interface IntSymbol : Symbol {
	var intValue: Long
}



private class IntSymbolImpl(
	override val base: SymBase,
	override var intValue: Long
) : IntSymbol



fun IntSymbol(base: SymBase, intValue: Long): IntSymbol {
	return IntSymbolImpl(base, intValue)
}



class Namespace(
	override val base: SymBase,
	override val thisScope: ScopeIntern
) : ScopedSymbol



class LabelSymbol(
	override val base: SymBase,
) : PosSymbol {
	override var section = Section.TEXT
	override var pos = 0
}



class DllImportSymbol(
	override val base: SymBase
) : PosSymbol {
	override var section = Section.IDATA
	override var pos = 0
}



class DllSymbol(
	override val base: SymBase,
	val imports: MutableList<DllImportSymbol>
) : Symbol



class VarSymbol(
	override val base: SymBase,
	val size: Int
) : PosSymbol {
	override var section = Section.DATA
	override var pos = 0
}



class ResSymbol(
	override val base: SymBase,
	var size: Int = 0
) : PosSymbol {
	override var section = Section.DATA
	override var pos = 0
}



class ConstSymbol(
	override val base: SymBase,
	override var intValue: Long = 0L
): IntSymbol



class EnumEntrySymbol(
	override var base     : SymBase,
	val ordinal           : Int,
	override var intValue : Long
) : IntSymbol {
	lateinit var parent: EnumSymbol
}



class EnumSymbol(
	override val base      : SymBase,
	override val thisScope : ScopeIntern,
	val entries            : List<EnumEntrySymbol>
) : ScopedSymbol