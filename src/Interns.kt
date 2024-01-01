package eyre

import java.util.ArrayList
import java.util.HashMap



abstract class Intern(val id: Int) {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	val isNull get() = id == 0
	val isNotNull get() = id != 0
}



// The first intern must represent a NULl value
abstract class Interner<K, V : Intern> {
	private var count = 0
	private val list = ArrayList<V>()
	private val map = HashMap<K, V>()
	abstract val creator: (id: Int, key: K) -> V
	fun add(key: K): V = map.getOrPut(key) { creator(count++, key).also(list::add) }
	operator fun get(id: Int) = list[id]
	operator fun get(key: K) = add(key)
}



data class PlaceKey(val parent: Int, val name: Int)



class Place(id: Int, val key: PlaceKey) : Intern(id) {

	private fun append(builder: StringBuilder) {
		if(key.parent != 0) {
			Place[key.parent].append(builder)
			builder.append('.')
		}

		if(key.name != 0)
			builder.append(Name[key.name].string)
	}

	override fun toString() = buildString(::append)

	val name get() = Name[key.name]
	fun child(name: Intern) = add(PlaceKey(id, name.id))

	companion object : Interner<PlaceKey, Place>() {
		override val creator = ::Place
		val NULL = add(PlaceKey(0, 0))
	}

}



class Name(id: Int, val string: String) : Intern(id) {
	override fun toString() = string
	companion object : Interner<String, Name>() {
		override val creator = ::Name
		val NULL = add("")
		val regs = Reg.entries.associateBy { add(it.string) }
		val mnemonics = Mnemonic.entries.associateBy { add(it.string) }
		val widths = Width.entries.associateBy { add(it.string) }
		val MAIN = add("main")
		val PROC = add("proc")
		val STRUCT = add("struct")
		val ENUM = add("enum")
	}
}