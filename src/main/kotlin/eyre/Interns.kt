package eyre

import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.enums.EnumEntries


interface Intern {
	val id: Int
	val isEmpty get() = id == 0
}



class InternRange<T>(private val range: IntRange, private val elements: List<T>) {
	operator fun contains(intern: Name) = intern.id in range
	operator fun get(intern: Name) = elements[intern.id - range.first]
}



class Name(override val id: Int, val hash: Int, val string: String) : Intern, Token {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = if(id == 0) "_" else string
}



class Scope(override val id: Int, val hash: Int, val array: IntArray) : Intern {
	val last get() = Names[array[array.size - 1]]
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = array.joinToString(transform = { Names[it].string }, separator = ".")
}



@JvmInline
value class NameArray(val array: IntArray) {
	val size get() = array.size
	operator fun get(index: Int) = Names[array[index]]
	override fun toString() = array.joinToString(".") { this[it].string }
}



abstract class Interner<K, V : Intern> {

	protected var count = 0

	private val list = ArrayList<V>()

	protected val map = HashMap<K, V>()

	protected fun addInternal(key: K, value: V): V {
		list += value
		map[key] = value
		return value
	}

	operator fun get(id: Int) = list[id]

}



object Names : Interner<String, Name>() {

	fun add(key: String) = map[key] ?: addInternal(key, Name(count++, key.hashCode(), key))

	operator fun get(key: String) = map[key] ?: addInternal(key, Name(count++, key.hashCode(), key))

	private fun<T : Enum<T>> createRange(elements: EnumEntries<T>, supplier: (T) -> String?): InternRange<T> {
		val range = IntRange(count, count + elements.size - 1)
		for(e in elements) supplier(e)?.let { if(it != "NONE") add(it) }
		return InternRange(range, elements)
	}

	val keywords     = createRange(Keyword.entries, Keyword::string)
	val widths       = createRange(Width.entries, Width::string)
	val varWidths    = createRange(Width.entries, Width::varString)
	val registers    = createRange(Reg.entries, Reg::string)
	val prefixes     = createRange(InsPrefix.entries, InsPrefix::string)
	val mnemonics    = createRange(Mnemonic.entries, Mnemonic::string)

	val EMPTY = add("")
	val MAIN  = add("main")
	val SIZE  = add("size")
	val COUNT = add("count")
	val INT   = add("int")
	val DEBUG = add("debug")

}



object Scopes : Interner<IntArray, Scope>() {

	fun add(key: IntArray, hash: Int) = map[key] ?: addInternal(key, Scope(count++, hash, key))

	fun add(key: IntArray) = add(key, key.contentHashCode())

	fun add(base: Scope, addition: Intern): Scope {
		val array = base.array.copyOf(base.array.size + 1)
		array[base.array.size] = addition.id
		return add(array)
	}

	fun add(base: Scope, addition: IntArray, size: Int): Scope {
		val array = base.array.copyOf(base.array.size + size)
		for(i in 0 until size) array[base.array.size + i] = addition[i]
		return add(array)
	}

	val EMPTY = add(IntArray(0), 0)

}