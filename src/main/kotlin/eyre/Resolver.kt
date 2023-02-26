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
		for(symbol in context.constSymbols) when(symbol) {
			is ConstIntSymbol -> resolveConst(symbol.node)
			is EnumSymbol -> resolveEnum(symbol.node)
		}
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



	private fun resolveRef(node: RefNode) {
		resolveSymbols(node.left)
		val left = node.left.symbol!!
		val name = node.right.name

		if(left is VarSymbol) {
			if(name == StringInterner.SIZE) {
				node.right.symbol = IntSymbol(ScopeInterner.EMPTY, name, left.size.toLong())
			} else {
				error("Invalid var reference")
			}
		} else {
			error("Invalid var reference")
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



	private fun resolve(symbol: ConstSymbol) {
		if(symbol.resolved) return
		if(symbol.resolving) error("Circular const dependency")
	}



	private fun resolveConst(node: ConstNode) {
		val symbol = node.symbol
		if(symbol.resolved) return
		if(symbol.resolving) error("Circular const dependency")
		symbol.resolving = true
		symbol.value = resolveConstRec(node.value)
		symbol.resolving = false
		symbol.resolved = true
	}



	private fun resolveEnum(node: EnumNode) {
		for(n in node.entries) {
			val symbol = n.symbol
			if(symbol.resolved) continue
			if(symbol.resolving) error("Circular const dependency")
			symbol.resolving = true
			symbol.value = resolveConstRec(n.value)
			symbol.resolving = false
			symbol.resolved = true
		}
	}



	private fun resolveConstRec(node: AstNode): Long {
		if(node is IntNode) return node.value

		if(node is UnaryNode) return node.op.calculate(resolveConstRec(node.node))

		if(node is BinaryNode)
			return node.op.calculate(resolveConstRec(node.left), resolveConstRec(node.right))

		if(node is SymProviderNode) {
			return when(val symbol = node.symbol ?: error("Unresolved symbol")) {
				is ConstIntSymbol -> {
					if(!symbol.resolved) resolveConst(symbol.node)
					return symbol.value
				}
				is EnumEntrySymbol -> {
					if(!symbol.resolved)
						resolveEnum(symbol.parent.node)
					symbol.value
				}
				is IntSymbol -> symbol.value
				else -> error("Invalid symbol: $symbol")
			}
		}

		error("Invalid integer constant node: $node")
	}

}