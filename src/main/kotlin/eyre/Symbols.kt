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
		fun empty(resolved: Boolean) = SymBase(Scopes.EMPTY, Names.EMPTY, resolved)
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
	val alignment get() = size
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



interface AnonSymbol : Symbol {
	override val base get() = SymBase.EMPTY
}



class AliasRefSymbol(val value: AstNode, val offset: Int) : AnonSymbol

class RefSymbol(val receiver: PosSymbol, val offset: Int, override val type: Type): AnonSymbol, PosSymbol, TypedSymbol {
	override var pos
		set(_) = error("Cannot set ref symbol pos")
		get() = receiver.pos + offset
	override var section
		set(_) = error("Cannot set ref symbol section")
		get() = receiver.section
}



/*
Types
 */



abstract class IntegerType(name: String, override val size: Int) : Type {
	override val base = SymBase(Scopes.EMPTY, Names[name], resolved = true)
}



class ArraySymbol(override val base: SymBase, override val type: Type): Type, TypedSymbol {
	var count = 0
	override val size get() = type.size * count
	override val alignment = type.alignment
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

class VarDbSymbol(
	override val base : SymBase,
	val size          : Int,
	override var type : Type = VoidType
) : TypedSymbol, PosSymbol {
	override var section = Section.DATA
	override var pos = 0
}

class VarAliasSymbol(
	override val base : SymBase,
	override var type : Type = VoidType
) : TypedSymbol



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
	override var alignment = 0
}


class Namespace(
	override val base: SymBase,
	override val thisScope: Scope
) : ScopedSymbol



class ProcSymbol(
	override val base: SymBase,
	override val thisScope: Scope,
	val hasStackNodes: Boolean
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