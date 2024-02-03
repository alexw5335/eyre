package eyre

import java.util.ArrayList
import java.util.HashMap

class SymTable {

	private data class Key(val parent: Sym?, val name: Name)
	val list = ArrayList<Sym>()
	private val map = HashMap<Key, Sym>()

	fun add(parent: Sym?, name: Name, sym: Sym): Sym? {
		val key = Key(parent, name)
		map[key]?.let { return it }
		map[key] = sym
		list += sym
		return null
	}

	fun add(sym: Sym) = add(sym.parent, sym.name, sym)
	fun add(name: Name, sym: Sym) = add(sym.parent, name, sym)
	fun get(parent: Sym?, name: Name) = map[Key(parent, name)]

}
