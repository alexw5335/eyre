package eyre

import java.util.ArrayList
import java.util.HashMap



class SymbolTable {

	private data class Key(val parent: Symbol, val name: Name)
	val list = ArrayList<Symbol>()
	private val map = HashMap<Key, Symbol>()

	init {
		add(RootSym)
	}

	fun add(parent: Symbol, name: Name, sym: Symbol): Symbol? {
		val key = Key(parent, name)
		map[key]?.let { return it }
		map[key] = sym
		list += sym
		return null
	}

	fun add(sym: Symbol) = add(sym.parent, sym.name, sym)

	fun add(name: Name, sym: Symbol) = add(sym.parent, name, sym)

	fun get(parent: Symbol, name: Name) = map[Key(parent, name)]

}



class Name(val id: Int, val string: String) {
	val isNull get() = id == 0
	val isNotNull get() = id != 0
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

	val widths = Width.entries.associateBy { get(it.string) }
	val regs = Reg.entries.associateBy { get(it.string) }
	val mnemonics = Mnemonic.entries.associateBy { get(it.string) }

	val NONE      = get("")
	val VAR       = get("var")
	val NAMESPACE = get("namespace")
	val FUN       = get("fun")
	val STRUCT    = get("struct")
	val UNION     = get("union")
	val ENUM      = get("enum")
	val TYPEDEF   = get("typedef")
	val CONST     = get("const")
	val BYTE      = get("byte")
	val WORD      = get("word")
	val DWORD     = get("dword")
	val QWORD     = get("qword")
	val COUNT     = get("count")
	val SIZE      = get("size")

}