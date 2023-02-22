package eyre

class Resolver(private val context: CompilerContext) {


	private var scopeStack = arrayOfNulls<ScopeIntern>(64)

	private var scopeStackSize = 0



	private fun pushScope(scope: ScopeIntern) {
		if(scopeStackSize >= scopeStack.size)
			scopeStack = scopeStack.copyOf(scopeStackSize * 2)
		scopeStack[scopeStackSize++] = scope
	}



	private fun popScope() {
		scopeStackSize--
	}



	fun resolve() {
		pushScope(ScopeInterner.EMPTY)
		context.srcFiles.forEach(::resolveFile)
	}



	private fun resolveFile(srcFile: SrcFile) {
		if(srcFile.resolving)
			error("Circular dependency found. Currently resolving files: ${context.srcFiles.filter { it.resolving }}")
		else if(srcFile.resolved)
			return

		srcFile.resolving = true

		for(node in srcFile.nodes) {
			when(node) {
				is NamespaceNode   -> pushScope(node.symbol.thisScope)
				is ScopeEndNode    -> popScope()
				is InstructionNode -> {
					resolveSymbols(node.op1 ?: continue)
					resolveSymbols(node.op2 ?: continue)
					resolveSymbols(node.op3 ?: continue)
					resolveSymbols(node.op4 ?: continue)
				}
				else -> continue
			}
		}

	}



	private fun resolveSymbols(node: AstNode) {
		when(node) {
			is UnaryNode  -> resolveSymbols(node.node)
			is BinaryNode -> { resolveSymbols(node.left); resolveSymbols(node.right) }
			is MemNode    -> resolveSymbols(node.value)
			is SymNode    -> resolveSymbol(node.name)
			is DotNode    -> resolveDot(node)
			else          -> { }
		}
	}



	private fun resolveSymbol(name: StringIntern): Symbol {
		for(i in scopeStackSize - 1 downTo 0) {
			val scope = scopeStack[i]!!
			context.symbols.get(scope, name)?.let { return it }
		}

		error("Unresolved symbol: $name")
	}



	private fun resolveDot(node: DotNode): Symbol {
		val scope = when(node.left) {
			is SymNode -> resolveSymbol(node.left.name)
			is DotNode -> resolveDot(node.left)
			else       -> error("Invalid receiver: ${node.left.printString}")
		}

		if(scope !is ScopedSymbol)
			error("Invalid receiver: $scope")

		return context.symbols.get(scope.thisScope, node.right.name)
			?: error("Unresolved symbol: ${node.right.name}")
	}


}