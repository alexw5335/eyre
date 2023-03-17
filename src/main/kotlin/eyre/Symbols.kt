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

	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}



interface Type : ScopedSymbol {
	val size: Int
	val properties: HashMap<StringIntern, Symbol>
}



interface ScopedSymbol : Symbol {
	val thisScope: ScopeIntern
	override val qualifiedName get() = "$thisScope"
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
) : IntSymbol {
	init { resolved = true }
}



fun IntSymbol(base: SymBase, intValue: Long): IntSymbol {
	return IntSymbolImpl(base, intValue)
}



class MemberSymbol(
	override val base: SymBase,
	val offset: Int,
	val size: Int
) : IntSymbol {
	override var intValue = offset.toLong()
	lateinit var parent: StructSymbol
}



class StructSymbol(
	override val base: SymBase,
	override val thisScope: ScopeIntern,
	val members: List<MemberSymbol>,
	val size: Int
) : ScopedSymbol



class Namespace(
	override val base: SymBase,
	override val thisScope: ScopeIntern
) : ScopedSymbol



class ProcSymbol(
	override val base: SymBase,
	override val thisScope: ScopeIntern
) : ScopedSymbol, PosSymbol {
	override var section = Section.TEXT
	override var pos = 0
	var size = 0
}



class LabelSymbol(
	override val base: SymBase
) : PosSymbol {
	override var section = Section.TEXT
	override var pos = 0
}



class DebugLabelSymbol(
	override val base: SymBase
) : PosSymbol {
	override var section = Section.TEXT
	override var pos = 0
}



class DllImportSymbol(
	override val base: SymBase,
) : PosSymbol {
	override var section = Section.IDATA
	override var pos = 0
}



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
	override var section = Section.BSS
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
) : IntSymbol



class EnumSymbol(
	override val base      : SymBase,
	override val thisScope : ScopeIntern,
	val entries            : List<EnumEntrySymbol>
) : ScopedSymbol