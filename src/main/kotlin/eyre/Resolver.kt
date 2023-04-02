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

		for(srcFile in context.srcFiles) {
			val prev = scopeStackSize
			scopeStackSize = 1

			for(node in srcFile.nodes) {
				when(node) {
					is NamespaceNode -> pushScope(node.symbol.thisScope)
					is ProcNode      -> pushScope(node.symbol.thisScope)
					is ScopeEndNode  -> popScope()
					is InsNode       -> resolveIns(node)
					is ConstNode     -> if(!node.symbol.resolved) resolveConst(node.symbol)
					is EnumNode      -> if(!node.symbol.resolved) resolveEnum(node.symbol)
					else             -> continue
				}
			}

			scopeStackSize = prev
		}
	}



	private fun resolveIns(node: InsNode) {
		if(node.mnemonic == Mnemonic.DLLCALL) return
		resolveNode(node.op1 ?: return)
		resolveNode(node.op2 ?: return)
		resolveNode(node.op3 ?: return)
		resolveNode(node.op4 ?: return)
	}



	private fun resolveNode(node: AstNode) { when(node) {
		is UnaryNode  -> resolveNode(node.node)
		is BinaryNode -> { resolveNode(node.left); resolveNode(node.right) }
		is MemNode    -> resolveNode(node.value)
		is NameNode   -> resolveName(node)
		is DotNode    -> resolveDot(node)
		is RefNode    -> resolveRef(node)
		else          -> return
	}}



	/*
	Symbol node resolution
	 */



	private fun resolveSymbol(name: Name): Symbol {
		for(i in scopeStackSize - 1 downTo 0) {
			val scope = scopeStack[i]!!
			context.symbols.get(scope, name)?.let { return it }
		}

		error("Unresolved symbol: $name")
	}



	private fun resolveDot(node: DotNode): Symbol {
		val scope = when(node.left) {
			is NameNode -> resolveSymbol(node.left.name)
			is DotNode  -> resolveDot(node.left)
			else        -> error("Invalid receiver: ${node.left.printString}")
		}

		if(scope !is ScopedSymbol)
			error("Invalid receiver: $scope")

		if(node.right !is NameNode)
			error("Invalid node: ${node.right}")

		return context.symbols.get(scope.thisScope, node.right.name)
			?: error("Unresolved symbol: ${node.right.name}")
	}



	private fun resolveName(node: NameNode): Symbol {
		val symbol = resolveSymbol(node.name)
		node.symbol = symbol
		return symbol
	}



	private fun resolveRef(node: AstNode): Symbol {
		error("Invalid node: $node")
	}



	/*
	Symbol resolution
	 */



	private fun Symbol.beginResolve(): Boolean {
		if(resolved) return true
		if(resolving) error("Circular dependency: $this")
		resolving = true
		return false
	}



	private fun resolveType(type: Type) {
		if(type.resolved) return

		when(type) {
			is EnumSymbol -> resolveEnum(type)
			else          -> error("Invalid type: $type")
		}
	}



	private fun resolveConst(symbol: ConstSymbol) {
		val node = symbol.node as ConstNode
		resolveNode(node.value)
		symbol.intValue = resolveInt(node.value)
		symbol.resolved = true
	}



	private fun resolveEnum(enumSymbol: EnumSymbol) {
		val enumNode = enumSymbol.node as EnumNode
		var current = if(enumSymbol.isBitmask) 1L else 0L
		var max = 0L

		for(node in enumNode.entries) {
			val symbol = node.symbol
			if(symbol.beginResolve()) continue

			if(node.value == null) {
				symbol.intValue = current
				if(enumSymbol.isBitmask) current *= 2 else current++
			} else {
				symbol.intValue = resolveInt(node.value)
				current = symbol.intValue
				if(enumSymbol.isBitmask && current.countOneBits() != 1)
					error("Bitmask entry must be power of two: ${symbol.intValue}")
			}

			max = max.coerceAtLeast(symbol.intValue)
			symbol.resolving = false
			symbol.resolved = true
		}

		enumSymbol.size = when {
			max.isImm8  -> 1
			max.isImm16 -> 2
			max.isImm32 -> 4
			else        -> 8
		}

		enumSymbol.resolved = true
	}



	private fun resolveStruct(structSymbol: StructSymbol) {

	}



	/*
	Int resolution
	 */



	private fun resolveIntSymbol(symbol: Symbol): Long {
		if(symbol !is IntSymbol) error("Invalid symbol: $symbol")
		if(symbol.beginResolve()) return symbol.intValue

		when(symbol) {
			is ConstSymbol     -> resolveConst(symbol)
			is EnumEntrySymbol -> resolveEnum(symbol.parent)
			else               -> error("Invalid symbol: $symbol")
		}

		symbol.resolving = false
		return symbol.intValue
	}



	private fun resolveInt(node: AstNode): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.op.calculate(resolveInt(node.node))
		is BinaryNode -> node.op.calculate(resolveInt(node.left), resolveInt(node.right))
		is SymNode    -> resolveIntSymbol(node.symbol!!)
		else          -> error("Invalid node: $node")
	}



}