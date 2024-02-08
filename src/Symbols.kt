package eyre



/*
All members of symbols should be mutable except for SymBase, whose members
are mutable anyway. Symbols are constructed in the parser in order to
populate the symbol table. Symbol data is filled in by the resolver and assembler.
 */



class SymBase(val parent: Sym?, val name: Name) {
	var resolved = false
	var pos: Pos = Pos.NULL
	companion object {
		val NULL = SymBase(null, Name.NONE)
	}
}



// Interfaces



sealed interface Sym {
	val base: SymBase
	val parent get() = base.parent
	val name get() = base.name
	var resolved get() = base.resolved; set(value) { base.resolved = value }
}

sealed interface AnonSym : Sym {
	override val base get() = SymBase.NULL
}

sealed interface IntSym : Sym {
	var intValue: Long
}

sealed interface PosSym : Sym {
	var pos: Pos get() = base.pos; set(value) { base.pos = value }
}

interface SizedSym : Sym {
	var size: Int
}

interface Type : SizedSym {
	var alignment: Int
}

interface TypedSym : Sym {
	var type: Type
}

data object NullType : Type, AnonSym {
	override var size = 0
	override var alignment = 0
}



// Symbols



class EnumEntrySym(
	override val base: SymBase,
	override var intValue: Long = 0
) : IntSym



class EnumSym(
	override val base      : SymBase,
	val entries            : ArrayList<EnumEntrySym> = ArrayList(),
	override var size      : Int = 0,
	override var alignment : Int = 0
) : Type



class NamespaceSym(override val base: SymBase) : Sym



class DllImportSym(name: Name) : PosSym {
	override val base = SymBase(null, name)
}



class TypedefSym(
	override val base: SymBase,
	override var type: Type = NullType
) : TypedSym



class ConstSym(
	override val base: SymBase,
	var value: Long = 0
) : Sym



class MemberSym(
	override val base: SymBase,
	override var type: Type = NullType,
	var offset: Int = 0,
	var index: Int = 0,
) : TypedSym



class StructSym(
	override val base: SymBase,
	val isUnion: Boolean,
	override var size: Int = 0,
	override var alignment: Int = 0,
	var members: ArrayList<MemberSym> = ArrayList()
) : Type



class LabelSym(override val base: SymBase) : PosSym

class ProcSym(override val base: SymBase) : PosSym