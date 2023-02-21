package eyre

class SymbolTable {


	private class Node(var value: Symbol?, var next: Node?)

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



	fun get(scope: ScopeIntern, name: StringIntern): Symbol? {
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



	fun getAll() = buildList {
		for(n in nodes) {
			n.value?.let(::add)
			var n2 = n.next
			while(n2 != null) {
				n2.value?.let(::add)
				n2 = n.next
			}
		}
	}


}