package eyre

import java.util.ArrayList
import java.util.HashMap




class SymbolTable {

	private data class Key(val parent: Int, val name: Int)

	private val list = ArrayList<Symbol>()
	private val map = HashMap<Key, Symbol>()
	val root = RootSym().also(::add)


	fun add(sym: Symbol): Symbol? {
		val key = Key(sym.place.parent, sym.place.name)
		map[key]?.let { return it }
		sym.place.id = list.size
		list.add(sym)
		map[key] = sym
		return null
	}

	fun get(parent: Int, name: Int) = map[Key(parent, name)]
	fun get(index: Int) = list[index]

}



class Place(val parent: Int, val name: Int, var id: Int = 0)



class Name(val id: Int, val string: String) {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
	val isNull get() = id == 0
	val isNotNull get() = id != 0
}



object Names {
	private var count = 0
	private val list = ArrayList<Name>()
	private val map = HashMap<String, Name>()
	operator fun get(id: Int) = list[id]
	operator fun get(key: String) = map.getOrPut(key) { Name(count++, key).also(list::add) }
	val NULL = get("")
	val regs = Reg.entries.associateBy { get(it.string) }
	val mnemonics = Mnemonic.entries.associateBy { get(it.string) }
	val widths = Width.entries.associateBy { get(it.string) }
	val MAIN = get("main")
	val PROC = get("proc")
	val STRUCT = get("struct")
	val ENUM = get("enum")
}