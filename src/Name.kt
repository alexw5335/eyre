package eyre

import java.util.ArrayList
import java.util.HashMap


class Name(val id: Int, val string: String) {

	enum class Type {
		NONE,
		MNEMONIC,
		WIDTH,
		REG,
		KEYWORD
	}

	var type = Type.NONE
	var mnemonic = Mnemonic.entries[0]
	var width = Width.entries[0]
	var reg = Reg.entries[0]
	var keyword = TokenType.entries[0]

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

		init {
			for(width in Width.entries)
				get(width.string).let { it.type = Type.WIDTH; it.width = width }
			for(reg in Reg.entries)
				get(reg.string).let { it.type = Type.REG; it.reg = reg }
			for(mnemonic in Mnemonic.entries)
				get(mnemonic.string).let { it.type = Type.MNEMONIC; it.mnemonic = mnemonic }
			for(keyword in TokenType.entries)
				if(keyword.isKeyword)
					get(keyword.string).let { it.type = Type.KEYWORD; it.keyword = keyword }
		}

	}
}