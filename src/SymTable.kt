package eyre

class SymTable : Iterable<Sym> {


	private data class Node(var value: Sym?, var next: Node?) : Iterable<Node> {
		override fun iterator() = object : Iterator<Node> {
			override fun hasNext() = next != null
			override fun next() = next!!
		}
	}



	private val nodes = Array(8192) { Node(null, null) }



	fun add(sym: Sym): Sym? {
		val hash = sym.place.hashCode()
		var node = nodes[hash and (nodes.size - 1)]

		if(node.value == null) {
			node.value = sym
			return null
		}

		var emptyNode: Node? = null

		while(true) {
			val value = node.value
			if(value == null)
				emptyNode = node
			else if(value.place == sym.place)
				return value
			node = node.next ?: break
		}

		if(emptyNode != null) {
			emptyNode.value = sym
			return null
		}

		node.next = Node(sym, null)
		return null
	}



	fun get(scope: Scope, name: Name) = get(Place(scope, name))



	fun get(place: Place): Sym? {
		val hash = place.hashCode()
		var node = nodes[hash and (nodes.size - 1)]

		if(node.value == null)
			return null

		while(true) {
			val value = node.value
			if(value != null && value.place == place)
				return value
			node = node.next ?: return null
		}
	}



	override fun iterator() = object : Iterator<Sym> {
		var index = 0
		var next: Node? = null

		init {
			for(i in nodes.indices) {
				if(nodes[i].value != null) {
					index = i + 1
					next = nodes[i]
					break
				}
			}
		}

		override fun hasNext() = next != null && next!!.value != null

		override fun next(): Sym {
			val value = next!!.value!!

			if(next!!.next != null) {
				next = next!!.next
				return value
			}

			for(i in index ..< nodes.size) {
				if(nodes[i].value != null) {
					index = i + 1
					next = nodes[i]
					return value
				}
			}

			next = null
			return value
		}
	}


}