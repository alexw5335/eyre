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
		if(file.resolving)
			err(null, "Cyclic compile-time file dependency")
		file.resolving = true
		visit(file, ::resolveNode)
		file.resolved = true
		file.resolving = false
	}



	/*
	Name resolution
	 */



	private fun resolveName(name: Name): Symbol {
		for(i in scopeStack.indices.reversed()) {
			val scope = scopeStack[i]
			context.symTable.get(scope, name)?.let { return it }
		}

		err(null, "Unresolved symbol: $name")
	}



	private fun resolveNodeFile(node: Node) {
		if(node.srcPos == null)
			err(null, "Missing SrcPos: $node")
		resolveNodesInFile(node.srcPos!!.file)
	}



	private fun resolveInt(node: Node): Int {
		fun sym(sym: Symbol?): Int {
			if(sym == null)
				err(node.srcPos, "Unresolved symbol")
			if(!sym.resolved)
				if(sym is Node)
					resolveNodeFile(sym)
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
		is NameNode      -> node.symbol = resolveName(node.value)
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

		is RegNode, is StringNode, is IntNode, is LabelNode -> Unit

		NullNode -> context.internalErr("Encountered NullNode")
	}}


}