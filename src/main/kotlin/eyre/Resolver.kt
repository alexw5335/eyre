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

		when(left) {
			is VarSymbol -> {
				when(name) {
					StringInterner.SIZE -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.size.toLong())
					else -> error("Invalid var reference")
				}
			}
			is EnumSymbol -> {
				when(name) {
					StringInterner.SIZE -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.entries.size.toLong())
					else -> error("Invalid enum reference")
				}
			}
			else -> error("Invalid reference")
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



	private fun<T : Symbol> T.doResolve(block: T.() -> Unit) {
		if(resolving) error("Circular const dependency")
		resolving = true
		block(this)
		resolving = false
		resolved = true
	}



	private fun resolveConstIntSymbol(symbol: IntSymbol): Long {
		if(symbol.resolved) return symbol.intValue

		when(symbol) {
			is ConstSymbol     -> symbol.doResolve { intValue = resolveConstInt(symbol.node.value) }
			is EnumEntrySymbol -> symbol.doResolve { intValue = resolveConstInt(symbol.node.value) }
			else               -> error("Invalid int symbol")
		}

		return symbol.intValue
	}



	private fun resolveConstInt(node: AstNode): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.op.calculate(resolveConstInt(node.node))
		is BinaryNode -> node.op.calculate(resolveConstInt(node.left), resolveConstInt(node.right))

		is SymProviderNode -> when(val symbol = node.symbol ?: error("Unresolved symbol")) {
			is IntSymbol   -> resolveConstIntSymbol(symbol)
			else           -> error("Invalid symbol: $symbol")
		}

		else -> error("Invalid integer constant node: $node")
	}



	private fun resolveEnum(node: EnumNode) {

	}


}