package eyre

class SymbolTable : Iterable<Symbol> {


	private data class Node(var value: Symbol?, var next: Node?) : Iterable<Node> {
		override fun iterator() = object : Iterator<Node> {
			override fun hasNext() = next != null
			override fun next() = next!!
		}
	}



	private val nodes = Array(1024) { Node(null, null) }



	fun add(symbol: Symbol): Symbol? {
		val hash = symbol.scope.id * 31 + symbol.name.id
		var node = nodes[hash and (nodes.size - 1)]

		if(node.value == null) {
			node.value = symbol
			return null
		}

		var emptyNode: Node? = null

		while(true) {
			val value = node.value
			if(value == null)
				emptyNode = node
			else if(value.scope == symbol.scope && value.name == symbol.name)
				return value
			node = node.next ?: break
		}

		if(emptyNode != null) {
			emptyNode.value = symbol
			return null
		}

		node.next = Node(symbol, null)
		return null
	}



	fun get(scope: Scope, name: Name): Symbol? {
		val hash = scope.id * 31 + name.id
		var node = nodes[hash and (nodes.size - 1)]

		if(node.value == null)
			return null

		while(true) {
			val value = node.value
			if(value != null && value.scope == scope && value.name == name)
				return value
			node = node.next ?: return null
		}
	}



	override fun iterator() = object : Iterator<Symbol> {
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

		override fun next(): Symbol {
			val value = next!!.value!!

			if(next!!.next != null) {
				next = next!!.next
				return value
			}

			for(i in index until nodes.size) {
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