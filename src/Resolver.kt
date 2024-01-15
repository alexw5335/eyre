package eyre

import java.util.Stack

class Resolver(private val context: Context) {


	private var scopeStack = Stack<Symbol>()



	fun resolve() {
		pushScope(context.symTable.root)
		context.files.forEach(::resolveNodesInFile)
		popScope()
	}

	private fun pushScope(scope: Symbol) = scopeStack.push(scope)

	private fun popScope() = scopeStack.pop()

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(message, srcPos)

	private fun visit(file: SrcFile, block: (Node) -> Unit) {
		for(node in file.nodes) {
			try {
				block(node)
			} catch(e: EyreError) {
				file.invalid = true
				break
			}
		}
	}

	private fun resolveNodesInFile(file: SrcFile) {
		if(file.resolved)
			return
		file.resolving = true
		visit(file, ::resolveNode)
		file.resolving = false
		file.resolved = true
	}



	/*
	Name resolution
	 */



	private fun resolveName(srcPos: SrcPos?, name: Name): Symbol {
		for(i in scopeStack.indices.reversed()) {
			val scope = scopeStack[i]
			context.symTable.get(scope, name)?.let { return it }
		}

		err(srcPos, "Unresolved symbol: $name")
	}



	private fun resolveNodeFile(srcNode: Node, node: Node) {
		val file = node.srcPos?.file ?: context.internalErr()
		if(file.resolving)
			err(srcNode.srcPos, "Cyclic resolution")
		resolveNodesInFile(file)
	}



	private fun resolveInt(node: Node): Int {
		fun sym(sym: Symbol?): Int {
			if(sym == null)
				err(node.srcPos, "Unresolved symbol")
			if(!sym.resolved)
				if(sym is Node)
					resolveNodeFile(node, sym)
				else
					context.internalErr()
			if(sym is IntSym)
				return sym.intValue
			err(node.srcPos, "Invalid int node: $node")
		}

		return when(node) {
			is IntNode  -> node.value
			is UnNode   -> node.calc(::resolveInt)
			is BinNode  -> node.calc(::resolveInt)
			is NameNode -> sym(node.symbol)
			else        -> err(node.srcPos, "Invalid int node: $node")
		}
	}



	private fun resolveNode(node: Node) { when(node) {
		is NamespaceNode -> pushScope(node.scope)
		is ProcNode      -> pushScope(node.scope)
		is ScopeEndNode  -> popScope()
		is NameNode      -> node.symbol = resolveName(node.srcPos, node.value)
		is UnNode        -> resolveNode(node.child)

		is BinNode -> {
			resolveNode(node.left)
			resolveNode(node.right)
		}

		is ConstNode -> {
			resolveNode(node.valueNode)
			node.intValue = resolveInt(node.valueNode)
			node.resolved = true
		}

		is EnumNode -> {
			pushScope(node.scope)

			var current = 0
			for(entry in node.entries) {
				entry.intValue = if(entry.valueNode != null) {
					resolveNode(entry.valueNode)
					resolveInt(entry.valueNode)
				} else {
					current
				}

				current = entry.intValue + 1
				entry.resolved = true
			}

			node.resolved = true

			popScope()
		}

		is RegNode, is StringNode, is IntNode, is LabelNode -> Unit

		NullNode -> context.internalErr("Encountered NullNode")
	}}


}