package eyre



/*
Interfaces
 */



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



interface TypedSymbol : Symbol {
	val type: Type
}



interface ScopedSymbol : Symbol {
	val thisScope: Scope
}



interface PosSymbol : Symbol {
	var section: Section
	var pos: Int
}



interface IntSymbol : Symbol {
	var intValue: Long
}



/*
Types
 */



abstract class IntegerType(name: String, override val size: Int) : Type {
	override val base = SymBase(Scopes.EMPTY, Names[name], resolved = true)
}



class ArrayType(override val base: SymBase, override val type: Type, var count: Int): Type, TypedSymbol {
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



/*
Symbols
 */



private class IntSymbolImpl(
	override val base: SymBase,
	override var intValue: Long
) : IntSymbol {
	init { resolved = true }
}



fun IntSymbol(base: SymBase, intValue: Long): IntSymbol {
	return IntSymbolImpl(base, intValue)
}



class VarResSymbol(
	override val base : SymBase,
	override var type : Type = VoidType
) : TypedSymbol, PosSymbol {
	override var section = Section.BSS
	override var pos = 0
}



class MemberSymbol(
	override val base: SymBase,
	var type: Type = VoidType,
) : IntSymbol {
	var size = 0
	var offset = 0
	override var intValue = offset.toLong()
}



class StructSymbol(
	override val base      : SymBase,
	override val thisScope : Scope,
	val members            : List<MemberSymbol>
) : Type, ScopedSymbol {
	override var size = 0
}


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



class DllImportSymbol(
	override val base: SymBase,
) : PosSymbol {
	override var section = Section.IDATA
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
	override val thisScope : Scope,
	val entries            : List<EnumEntrySymbol>,
	val isBitmask          : Boolean
) : Type, ScopedSymbol {
	override var size = 0
}



class TypedefSymbol(
	override val base: SymBase,
	var type: Type
) : Type {
	override val size get() = type.size
}