package eyre

import kotlin.math.max

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

				is EnumNode      -> {
					if(node.symbol.resolved) continue
					pushScope(node.symbol.thisScope)
					resolveEnum(node, node.symbol)
					popScope()
				}
				is DbNode    -> for(part in node.parts) for(n in part.nodes) resolveSymbols(n)
				is TypedefNode   -> if(!node.symbol.resolved) resolveTypedef(node, node.symbol)
				is ConstNode     -> if(!node.symbol.resolved) resolveConst(node)
				is StructNode    -> if(!node.symbol.resolved) resolveStruct(node, node.symbol)
				is ResNode       -> resolveRes(node)
				else             -> continue
			}
		}

		scopeStackSize = prev
		srcFile.resolving = false
		srcFile.resolved = true
	}



	private fun resolveVar(node: VarNode, symbol: VarSymbol) {
		node.value?.let(::resolveSymbols)
		resolveSymbols(node.type)
		val type = getType(node.type)
		resolveType(type)

		when(symbol) {
			is AliasVarSymbol -> symbol.type = type
			is DbVarSymbol    -> symbol.type = type
			is ResVarSymbol   -> symbol.type = type
		}
	}



	private fun resolveTypedef(node: TypedefNode, symbol: TypedefSymbol) {
		resolveSymbols(node.value)
		val type = getType(node.value)
		symbol.type = type
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
			is SymDotNode -> node.symbol = resolveSymbol(node.names)
			is DotNode    -> node.right.symbol = resolveDot(node)
			is RefNode    -> resolveRef(node)
			else          -> { }
		}
	}



	private fun resolveSymbol(names: NameArray): Symbol {
		var left = resolveSymbol(names[0])
		for(i in 0 until names.size) {
			if(left !is ScopedSymbol) error("Expecting scoped symbol")
			left = context.symbols.get(left.thisScope, names[i]) ?: error("Symbol not found")
		}
		return left
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



	private fun getSymbol(node: AstNode) =
		(node as? SymProviderNode)?.symbol ?: error("Invalid symbol provider: $node")



	private fun getType(node: AstNode) =
		getSymbol(node) as? Type ?: error("Invalid type: $node")



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

		when(val name = node.right.name) {
			Names.SIZE -> when(left) {
				//is DbVarSymbol -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.size.toLong())
				is Type -> {
					resolveType(left)
					node.right.symbol = IntSymbol(SymBase.EMPTY, left.size.toLong())
				}
				else -> error("Invalid reference: $name")
			}

			Names.COUNT -> when(left) {
				is EnumSymbol -> node.right.symbol = IntSymbol(SymBase.EMPTY, left.entries.size.toLong())
				else -> error("Invalid reference: $name")
			}

			else -> error("Invalid reference: $name")
		}
	}



	private fun resolveType(type: Type) {
		if(type.resolved) return
		when(type) {
			is StructSymbol  -> resolveStruct(type.node as StructNode, type)
			is TypedefSymbol -> resolveTypedef(type.node as TypedefNode, type)
			else             -> error("Unhandled type: $type")
		}
	}



	private fun resolveStruct(structNode: StructNode, structSymbol: StructSymbol) {
		if(structSymbol.resolved) return

		if(structSymbol.manual) {
			for(member in structNode.members) {
				resolveSymbols(member.type)
				val type = getType(member.type)
				resolveType(type)
				member.symbol.type = type
				member.symbol.size = type.size
				member.symbol.resolved = true
			}

			structSymbol.resolved = true
			return
		}

		var offset = 0
		var maxAlignment = 0

		for(member in structNode.members) {
			resolveSymbols(member.type)
			val type = getType(member.type)
			resolveType(type)
			val size = type.size
			member.symbol.type = type
			member.symbol.size = size
			val alignment = size.coerceAtMost(8)
			maxAlignment = max(alignment, maxAlignment)
			offset = (offset + alignment - 1) and -alignment
			member.symbol.offset = offset
			offset += size
			member.symbol.resolved = true
			member.symbol.intValue = member.symbol.offset.toLong()
		}

		structSymbol.resolved = true
		structSymbol.size = (offset + maxAlignment - 1) and -maxAlignment
	}



	private fun resolveConst(node: ConstNode, symbol: ConstSymbol){
		resolveSymbols(node.value)
		symbol.resolving = true
		symbol.intValue = resolveInt(node.value)
		symbol.resolving = false
		symbol.resolved = true
	}



	private fun resolveIntSymbol(symbol: IntSymbol): Long {
		if(symbol.resolved) return symbol.intValue
		if(symbol.resolving) error("Circular dependency: ${symbol.qualifiedName}")

		when(symbol) {
			is ConstSymbol -> {
				resolveConst(symbol.node as ConstNode, symbol)
			}

			is MemberSymbol -> {
				if(symbol.parent.resolving)
					error("Circular dependency: ${symbol.qualifiedName}")
				resolveStruct(symbol.parent.node as StructNode, symbol.parent)
			}

			is EnumEntrySymbol -> {
				if(symbol.parent.resolving)
					error("Circular dependency: ${symbol.qualifiedName}")
				resolveEnum(symbol.parent.node as EnumNode, symbol.parent)
			}

			else -> {
				error("Unhandled int symbol: $symbol")
			}
		}

		if(!symbol.resolved) error("Unresolved symbol")
		return symbol.intValue
	}



	private fun resolveEnum(enumNode: EnumNode, enumSymbol: EnumSymbol) {
		var current = if(enumSymbol.isBitmask) 1L else 0L

		for(node in enumNode.entries) {
			val symbol = node.symbol
			if(symbol.resolved) continue
			if(symbol.resolving) error("Circular dependency")
			symbol.resolving = true

			if(node.value == null) {
				symbol.intValue = current
				current += if(enumSymbol.isBitmask)
					current
				else
					1
			} else {
				resolveSymbols(node.value)
				symbol.intValue = resolveInt(node.value)
				current = symbol.intValue
				if(enumSymbol.isBitmask && current.countOneBits() != 1)
					error("Bitmask entry must be power of two: ${symbol.intValue}")
			}

			symbol.resolving = false
			symbol.resolved = true
		}

		enumSymbol.resolved = true
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