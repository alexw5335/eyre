package eyre



class SymBase(
	val scope     : Scope,
	val name      : Name,
	var resolved  : Boolean = false,
	var resolving : Boolean = false,
	var node      : AstNode? = null
) {

	companion object {
		val EMPTY = SymBase(Scopes.EMPTY, Names.EMPTY)
	}

}



interface Symbol {

	val base: SymBase

	val scope     get() = base.scope
	val name      get() = base.name
	var resolved  get() = base.resolved  ; set(v) { base.resolved = v }
	var resolving get() = base.resolving ; set(v) { base.resolving = v }
	var node      get() = base.node      ; set(v) { base.node = v }

	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"

}



interface Type : Symbol {
	val size: Int
}



abstract class IntegerType(name: String, override val size: Int) : Type {
	override val base = SymBase(Scopes.EMPTY, Names[name], resolved = true)
}



interface TypedSymbol : Symbol {
	val type: Type
}



class ArraySymbol(override val base: SymBase, override val type: Type): Type, TypedSymbol {
	var count = 0
	override val size get() = type.size * count
}



object ByteType : IntegerType("byte", 1)

object WordType : IntegerType("word", 2)

object DwordType : IntegerType("dword", 4)

object QwordType : IntegerType("qword", 8)

object VoidType : Type {
	override val base = SymBase.EMPTY
	override val size = 0
}



interface ScopedSymbol : Symbol {
	// Should be the same as scope.name
	val thisScope: Scope
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
	var offset: Int,
	var size: Int,
	var type: Type,
) : IntSymbol {
	override var intValue = offset.toLong()
	lateinit var parent: StructSymbol
}



class StructSymbol(
	override val base: SymBase,
	override val thisScope: Scope,
	val members: List<MemberSymbol>,
	override var size: Int,
	val manual: Boolean
) : Type, ScopedSymbol



class Namespace(
	override val base: SymBase,
	override val thisScope: Scope
) : ScopedSymbol



class ProcSymbol(
	override val base: SymBase,
	override val thisScope: Scope
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



sealed interface VarSymbol : TypedSymbol



class VarSym(
	override val base: SymBase,
	override var type: VoidType
) : TypedSymbol, PosSymbol {
	override var section = Section.DATA
	override var pos = 0
}

class AliasVarSymbol(
	override val base: SymBase,
	override var type: Type = VoidType
) : VarSymbol



class DbVarSymbol(
	override val base: SymBase,
	override var type: Type = VoidType
) : PosSymbol, TypedSymbol, VarSymbol {
	override var section = Section.DATA
	override var pos = 0
}


class ResVarSymbol(
	override val base: SymBase,
	override var type: Type = VoidType,
	var size: Int = 0
) : PosSymbol, VarSymbol {
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
) : IntSymbol {
	lateinit var parent: EnumSymbol
}



class EnumSymbol(
	override val base      : SymBase,
	override val thisScope : Scope,
	val entries            : List<EnumEntrySymbol>,
	val isBitmask          : Boolean
) : ScopedSymbol



class TypedefSymbol(
	override val base: SymBase,
	var type: Type
) : Type {
	override val size get() = type.size
}