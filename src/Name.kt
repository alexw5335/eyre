package eyre

import java.util.ArrayList
import java.util.HashMap


class Name(val id: Int, val string: String) {

	var keyword: TokenType? = null
	val isNull get() = id == 0
	val isNotNull get() = id != 0
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
	
	companion object {
		private var count = 0
		private val list = ArrayList<Name>()
		private val map = HashMap<String, Name>()
		operator fun get(id: Int) = list[id]
		operator fun get(key: String) = map.getOrPut(key) { Name(count++, key) }

		val NONE = get("")
		val MAIN = get("main")
		val SIZE = get("size")
		val COUNT = get("count")
		val OFFSET = get("offset")

		init {
			for(keyword in TokenType.entries)
				if(keyword.isKeyword)
					get(keyword.string).keyword = keyword
		}

	}
}