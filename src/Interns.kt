package eyre

import java.util.ArrayList
import java.util.HashMap




object SymbolTable {

	private class Node {
		var sym: Symbol? = null
		var next: Node? = null
	}

	private val list = ArrayList<Symbol>()
	private var nodes = Array(4096) { Node() }

	fun add(sym: Symbol): Boolean {
		val parent = sym.place.parent
		val name = sym.place.name
		sym.place.id = list.size
		list.add(sym)
		var node = nodes[(parent * 31 + name) % nodes.size]
		while(true) {
			val sym2 = node.sym ?: break
			if(sym2.place.parent == parent && sym2.place.name == name)
				return false
			if(node.next != null) {
				node = node.next!!
			} else {
				node.next = Node()
				node = node.next!!
				break
			}
		}
		node.sym = sym
		return true
	}

	fun getPlace(id: Int) = list[id].place

	fun get(parent: Int, name: Int): Symbol? {
		var node = nodes[(parent * 31 + name) % nodes.size]
		while(true) {
			val sym = node.sym ?: return null
			if(sym.place.parent == parent && sym.place.name == name) return sym
			node = node.next ?: return null
		}
	}

}



class Place(val parent: Int, val name: Int, var id: Int = 0) {
	private fun append(builder: StringBuilder) {
		if(parent != 0) {
			SymbolTable.getPlace(parent).append(builder)
			builder.append('.')
		}

		if(name != 0)
			builder.append(Names[name].string)
	}

	override fun toString() = buildString(::append)
}



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