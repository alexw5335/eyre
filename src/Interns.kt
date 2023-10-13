package eyre

import java.util.ArrayList
import java.util.HashMap



abstract class Intern(val id: Int) {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	val isNull get() = id == 0
	val isNotNull get() = id != 0
}



abstract class Interner<K, V : Intern> {
	private var count = 0
	private val list = ArrayList<V>()
	private val map = HashMap<K, V>()
	abstract val creator: (id: Int, key: K) -> V
	fun add(key: K): V = map.getOrPut(key) { creator(count++, key).also(list::add) }
	operator fun get(id: Int) = list[id]
	operator fun get(key: K) = add(key)
}



class Name(id: Int, val string: String) : Intern(id), Token {
	override fun toString() = string

	companion object : Interner<String, Name>() {
		override val creator = ::Name

		val NULL = add("")
		val MAIN = add("main")
		val PROC = add("proc")
		val SIZE = add("size")
		val COUNT = add("count")
		val ENUM = add("enum")
		val STRUCT = add("struct")
		val CONST = add("const")
		val NAMESPACE = add("namespace")

		val regs      = Reg.entries.associateBy { add(it.string) }
		val mnemonics = Mnemonic.entries.associateBy { add(it.string) }
		val widths    = Width.entries.associateBy { add(it.string) }
	}
}



class Scope(id: Int, val array: IntArray) : Intern(id) {
	val last get() = Name[array.last()]

	override fun toString() = array.joinToString(transform = { Name[it].string }, separator = ".")

	fun add(value: Name) = add(array + value.id)

	fun add(addition: IntArray, size: Int): Scope {
		val array = array.copyOf(array.size + size)
		for(i in 0 ..< size) array[array.size + i] = addition[i]
		return add(array)
	}

	companion object : Interner<IntArray, Scope>() {
		override val creator = ::Scope
		val NULL = add(IntArray(0))
	}
}




/*abstract class Intern(val id: Int) {
	val isNull get() = id == 0
	val isNotNull get() = id != 0
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
}



class InternRange<T>(private val range: IntRange, private val elements: List<T>) {
	operator fun contains(intern: Intern) = intern.id in range
	operator fun get(intern: Intern) = elements[intern.id - range.first]
}



class Name(id: Int, val string: String) : Intern(id), Token {
	override fun toString() = if(id == 0) "_" else string
}



class Scope(id: Int, val names: IntArray) : Intern(id) {
	val last get() = Names[names[names.size - 1]]
	override fun toString() = names.joinToString(transform = { Names[it].string }, separator = ".")
}



abstract class Interner<K, V : Intern> {

	protected var count = 0
	private val list = ArrayList<V>()
	private val map = HashMap<K, V>()

	protected abstract fun create(key: K): V
	operator fun get(id: Int) = list[id]

	fun add(key: K): V = map.getOrPut(key) { create(key).also(list::add) }

}



object Names : Interner<String, Name>() {

	val NULL = Name(0, "")

	override fun create(key: String) = Name(count++, key)

	private fun<T : Enum<T>> range(elements: EnumEntries<T>, supplier: (T) -> String?): InternRange<T> {
		val range = IntRange(count, count + elements.size - 1)
		for(e in elements) supplier(e)?.let { if(it != "NONE") add(it) }
		return InternRange(range, elements)
	}

	val widths    = range(Width.entries, Width::string)
	val registers = range(Reg.entries, Reg::string)
	val mnemonics = range(Mnemonic.entries, Mnemonic::string)

	val MAIN  = add("main")
	val SIZE  = add("size")
	val COUNT = add("count")
	val INT   = add("int")
	val DEBUG = add("debug")

}



object Scopes : Interner<IntArray, Scope>() {

	val NULL = Scope(0, IntArray(0))

	override fun create(key: IntArray) = Scope(count++, key)

	fun add(base: Scope, addition: Intern): Scope {
		val array = base.names.copyOf(base.names.size + 1)
		array[base.names.size] = addition.id
		return add(array)
	}

	fun add(base: Scope, addition: IntArray, size: Int): Scope {
		val array = base.names.copyOf(base.names.size + size)
		for(i in 0 ..< size) array[base.names.size + i] = addition[i]
		return add(array)
	}

}*/