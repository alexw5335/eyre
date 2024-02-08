package eyre

import java.util.*

class Resolver(private val context: Context) {


	private var scopeStack = Stack<Sym>()



	fun resolve() {
		context.files.forEach(::resolveNodesInFile)
	}

	private fun pushScope(scope: Sym) = scopeStack.push(scope)

	private fun popScope() = scopeStack.pop()

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(srcPos, message)

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
		if(file.resolved) return
		scopeStack.clear()
		file.resolving = true
		//visit(file, ::resolveNodeType)
		visit(file, ::resolveNode)
		file.resolving = false
		file.resolved = true
	}



	private fun resolveNodeFile(srcNode: Node, node: Node) {
		val file = node.srcPos?.file ?: context.internalErr()
		if(file.resolving)
			err(srcNode.srcPos, "Cyclic resolution")
		resolveNodesInFile(file)
	}



	/*
	Name resolution
	 */



	private fun resolveNames(srcPos: SrcPos?, names: List<Name>): Sym {
		var sym = resolveName(srcPos, names[0])
		for(i in 1 ..< names.size) {
			sym = resolveName(srcPos, sym, names[i])
		}
		return sym
	}



	private fun resolveName(srcPos: SrcPos?, scope: Sym, name: Name): Sym {
		return context.symTable.get(scope, name)
			?: err(srcPos, "Unresolved symbol: $name")
	}



	private fun resolveName(srcPos: SrcPos?, name: Name): Sym {
		context.symTable.get(null, name)?.let { return it }
		for(i in scopeStack.indices.reversed())
			context.symTable.get(scopeStack[i], name)?.let { return it }
		err(srcPos, "Unresolved symbol: $name")
	}



	// Symbol resolution



	private fun resolveScopeNodes(scope: Sym, children: List<Node>) {
		pushScope(scope)
		children.forEach(::resolveNode)
		popScope()
	}



	private fun resolveNode(node: Node) { when(node) {
		is UnNode   -> resolveNode(node.child)
		is NameNode -> node.sym = resolveName(node.srcPos, node.value)
		is OpNode   -> node.child?.let(::resolveNode)
		is BinNode  -> {
			resolveNode(node.left)
			resolveNode(node.right)
		}
		is ProcNode -> {
			if(node.sym.name == Name.MAIN) context.entryPoint = node.sym
			resolveScopeNodes(node.sym, node.children)
		}
		is NamespaceNode -> resolveScopeNodes(node.sym, node.children)
		is InsNode  -> {
			node.op1?.let(::resolveNode)
			node.op2?.let(::resolveNode)
			node.op3?.let(::resolveNode)
		}
		else -> { }
	}}





}