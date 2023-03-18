package eyre

class Resolver(private val context: CompilerContext) {


	private var scopeStack = arrayOfNulls<Scope>(64)

	private var scopeStackSize = 0



	/*
	Scope
	 */



	private fun pushScope(scope: Scope) {
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
		pushScope(Scopes.EMPTY)
		context.srcFiles.forEach(::resolveFile)
	}



	private fun resolveFile(srcFile: SrcFile) {
		if(srcFile.resolved)
			return
		if(srcFile.resolving)
			error("Circular dependency found. Currently resolving files: ${context.srcFiles.filter { it.resolving }}")

		srcFile.resolving = true

		val prev = scopeStackSize
		scopeStackSize = 1

		for(node in srcFile.nodes) {
			when(node) {
				is NamespaceNode -> pushScope(node.symbol.thisScope)
				is ProcNode      -> pushScope(node.symbol.thisScope)
				is ScopeEndNode  -> popScope()
				is InsNode       -> resolveInstruction(node)
				is ConstNode     -> resolveConst(node)
				is EnumNode      -> {
					pushScope(node.symbol.thisScope)
					resolveEnum(node)
					popScope()
				}
				is VarNode       -> for(part in node.parts) for(n in part.nodes) resolveSymbols(n)
				is ResNode       -> resolveRes(node)
				else             -> continue
			}
		}

		scopeStackSize = prev
		srcFile.resolving = false
		srcFile.resolved = true
	}



	private fun resolveInstruction(node: InsNode) {
		if(node.mnemonic == Mnemonic.DLLCALL) return
		resolveSymbols(node.op1 ?: return)
		resolveSymbols(node.op2 ?: return)
		resolveSymbols(node.op3 ?: return)
		resolveSymbols(node.op4 ?: return)
	}



	/**
	 * Recursively resolves symbols
	 */
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



	/**
	 * Resolves a symbol given a name
	 */
	private fun resolveSymbol(name: Name): Symbol {
		for(i in scopeStackSize - 1 downTo 0) {
			val scope = scopeStack[i]!!
			context.symbols.get(scope, name)?.let { return it }
		}

		for(i in scopeStackSize - 1 downTo 0) {
			println(scopeStack[i])
		}

		error("Unresolved symbol: $name")
	}



	private fun resolveRes(node: ResNode) {
		if(node.symbol.resolved) return
		resolveSymbols(node.size)
		node.symbol.size = resolveInt(node.size).toInt()
		node.symbol.resolved = true
	}



	private fun resolveConst(node: ConstNode) {
		if(node.symbol.resolved) return
		resolveSymbols(node.value)
		node.symbol.intValue = resolveInt(node.value)
		node.symbol.resolved = true
	}



	private fun resolveEnum(node: EnumNode) {
		for(i in 0 until node.entries.size) {
			val symbol = node.symbol.entries[i]
			if(symbol.resolved) continue
			resolveSymbols(node.entries[i].value!!)
			symbol.intValue = resolveInt(node.entries[i].value!!)
			symbol.resolved = true
		}
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



	private fun resolveRef(node: RefNode) {
		resolveSymbols(node.left)
		val left = node.left.symbol!!
		val name = node.right.name

		when(left) {
			is VarSymbol -> {
				when(name) {
					Names.SIZE -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.size.toLong())
					else -> error("Invalid var reference")
				}
			}
			is EnumSymbol -> {
				when(name) {
					Names.SIZE -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.entries.size.toLong())
					else -> error("Invalid enum reference")
				}
			}
			is StructSymbol -> {
				when(name) {
					Names.SIZE -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.size.toLong())
					else -> error("Invlaid struct reference")
				}
			}
			else -> error("Invalid reference")
		}
	}



	private fun resolveIntSymbol(symbol: IntSymbol): Long {
		if(symbol.resolved) return symbol.intValue
		val file = symbol.srcPos?.file ?: error("Unresolved symbol: ${symbol.qualifiedName}")
		if(file.resolving) error("Invalid const ordering: ${symbol.name}")
		resolveFile(file)
		if(!symbol.resolved) error("Unresolved symbol")
		return symbol.intValue
	}



	private fun resolveInt(node: AstNode): Long = when(node) {
		is IntNode -> node.value
		is UnaryNode -> node.op.calculate(resolveInt(node.node))
		is BinaryNode -> node.op.calculate(resolveInt(node.left), resolveInt(node.right))

		is SymProviderNode -> when(val symbol = node.symbol ?: error("Unresolved symbol node: $node")) {
			is IntSymbol -> resolveIntSymbol(symbol)
			else -> error("Invalid symbol: $symbol")
		}

		else -> error("Invalid integer constant node: $node")
	}


}