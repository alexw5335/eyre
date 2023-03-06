package eyre

class Resolver(private val context: CompilerContext) {


	private var scopeStack = arrayOfNulls<ScopeIntern>(64)

	private var scopeStackSize = 0



	/*
	Scope
	 */



	private fun pushScope(scope: ScopeIntern) {
		if(scopeStackSize >= scopeStack.size)
			scopeStack = scopeStack.copyOf(scopeStackSize * 2)
		scopeStack[scopeStackSize++] = scope
	}



	private fun popScope() {
		scopeStackSize--
	}



	/*
	Resolving
	 */



	fun resolve() {
		pushScope(ScopeInterner.EMPTY)
		context.srcFiles.forEach(::resolveFile)
	}



	private fun resolveFile(srcFile: SrcFile) {
		if(srcFile.resolved)
			return
		if(srcFile.resolving)
			error("Circular dependency found. Currently resolving files: ${context.srcFiles.filter { it.resolving }}")

		srcFile.resolving = true

		val prev = scopeStackSize
		scopeStackSize = 0

		for(node in srcFile.nodes) {
			when(node) {
				is NamespaceNode   -> pushScope(node.symbol.thisScope)
				is ScopeEndNode    -> popScope()
				is InsNode -> {
					resolveSymbols(node.op1 ?: continue)
					resolveSymbols(node.op2 ?: continue)
					resolveSymbols(node.op3 ?: continue)
					resolveSymbols(node.op4 ?: continue)
				}
				is ConstNode  -> resolveSymbols(node.value)
				is VarNode    -> for(part in node.parts) for(n in part.nodes) resolveSymbols(n)
				is ResNode    -> resolveSymbols(node.size)
				else -> continue
			}
		}

		scopeStackSize = prev
		srcFile.resolving = false
		srcFile.resolved = true
	}



	private fun resolveSymbols(node: AstNode) {
		when(node) {
			is UnaryNode  -> resolveSymbols(node.node)
			is BinaryNode -> { resolveSymbols(node.left); resolveSymbols(node.right) }
			is MemNode    -> resolveSymbols(node.value)
			is SymNode    -> node.symbol = resolveSymbol(node.name)
			is DotNode    -> node.right.symbol = resolveDot(node)
			is RefNode    -> resolveRef(node)
			else          -> { }
		}
	}



}