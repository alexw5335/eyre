package eyre



/*
All members of symbols should be mutable except for SymBase, whose members
are mutable anyway. Symbols are constructed in the parser in order to
populate the symbol table. Symbol data is filled in by the resolver and assembler.
 */



class SymBase(val parent: Sym?, val name: Name) {
	var resolved = false
	var pos: Int = 0
	var section: Section = Section.NULL
}



// Interfaces



sealed interface AnySym

sealed interface Sym : AnySym {
	val base: SymBase
	val parent get() = base.parent
	val name get() = base.name
	var resolved get() = base.resolved; set(value) { base.resolved = value }
}

sealed interface PosSym : Sym {
	var pos get() = base.pos; set(value) { base.pos = pos }
	var section get() = base.section; set(value) { base.section = value }
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

data object NullType : Type {
	override val base = SymBase(null, Name.NONE)
	override var size = 0
	override var alignment = 0
}



// Symbols



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
	override var size: Int = 0,
	override var alignment: Int = 0,
	var members: ArrayList<MemberSym> = ArrayList()
) : Type



class LabelSym(override val base: SymBase) : PosSym

class ProcSym(override val base: SymBase) : PosSym