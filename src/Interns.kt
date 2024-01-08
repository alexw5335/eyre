package eyre

import java.util.ArrayList
import java.util.HashMap



class SymbolTable {

	private data class Key(val parent: Symbol, val name: Name)

	private val map = HashMap<Key, Symbol>()
	val root = RootSym().also(::add)

	fun add(sym: Symbol): Symbol? {
		val key = Key(sym.parent, sym.name)
		map[key]?.let { return it }
		map[key] = sym
		return null
	}

	fun get(parent: Symbol, name: Name) = map[Key(parent, name)]

}



class Name(val id: Int, val string: String) {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
}



object Names {
	private var count = 0
	private val list = ArrayList<Name>()
	private val map = HashMap<String, Name>()
	operator fun get(id: Int) = list[id]
	operator fun get(key: String) = map.getOrPut(key) { Name(count++, key).also(list::add) }

	val NONE = get("")

	val regs = Reg.entries.associateBy { get(it.string) }
	val mnemonics = Mnemonic.entries.associateBy { get(it.string) }
	val widths = Width.entries.associateBy { get(it.string) }

	val NULL      = get("null")
	val MAIN      = get("main")
	val NAMESPACE = get("namespace")
	val PROC      = get("proc")
	val STRUCT    = get("struct")
	val ENUM      = get("enum")
	val CONST     = get("const")

}