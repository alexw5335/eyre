package eyre



class SymBase(
	val scope     : ScopeIntern,
	val name      : StringIntern,
	var resolved  : Boolean = true,
) {

	var srcNode: AstNode? = null

	companion object {
		val EMPTY = SymBase(ScopeInterner.EMPTY, StringInterner.EMPTY)
	}

}



interface Symbol {
	val base: SymBase
	val scope    get() = base.scope
	val name     get() = base.name
	var resolved get() = base.resolved; set(v) { base.resolved = v }
}



interface ScopedSymbol : Symbol {
	val thisScope: ScopeIntern
}



interface PosSymbol : Symbol {
	val pos: SectionPos?
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
	override val base: SymBase
) : PosSymbol {
	override var pos = SectionPos()
}



class DllImportSymbol(
	override val base: SymBase
) : PosSymbol {
	override var pos = SectionPos()
}



class DllSymbol(
	override val base: SymBase,
	val imports: MutableList<DllImportSymbol>
) : Symbol



class VarSymbol(
	override val base: SymBase,
	val size: Int
) : PosSymbol {
	override var pos = SectionPos()
}



class ResSymbol(
	override val base: SymBase,
	var size: Int
) : PosSymbol {
	override var pos = SectionPos()
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