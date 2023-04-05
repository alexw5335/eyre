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

		for(srcFile in context.srcFiles) {
			val prev = scopeStackSize
			scopeStackSize = 1

			for(node in srcFile.nodes) {
				when(node) {
					is NamespaceNode -> pushScope(node.symbol.thisScope)
					is ProcNode      -> pushScope(node.symbol.thisScope)
					is ScopeEndNode  -> popScope()
					is InsNode       -> resolveIns(node)
					is VarResNode    -> if(!node.symbol.resolved) resolveVarRes(node)
					is TypedefNode   -> if(!node.symbol.resolved) resolveTypedef(node.symbol)
					is ConstNode     -> if(!node.symbol.resolved) resolveConst(node.symbol)
					is EnumNode      -> if(!node.symbol.resolved) resolveEnum(node.symbol)
					is StructNode    -> if(!node.symbol.resolved) resolveStruct(node.symbol)
					else             -> continue
				}
			}

			scopeStackSize = prev
		}
	}



	private fun resolveVarRes(node: VarResNode) {
		node.symbol.type = resolveType(node.type)
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



	private fun resolveSymbol(node: SymNode): Symbol = when(node) {
		is NameNode  -> resolveName(node)
		is DotNode   -> resolveDot(node)
		is RefNode   -> resolveRef(node)
		else         -> error("Invalid node: $node")
	}



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



	private fun resolveSymbol(names: Array<Name>): Symbol {
		var symbol = resolveSymbol(names[0])
		for(i in 1 until names.size) {
			if(symbol !is ScopedSymbol) error("Invalid receiver: $symbol")
			symbol = resolveSymbol(names[i])
		}
		return symbol
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



	private inline fun<reified T : ScopedSymbol> Symbol.parent() = 
		context.parentMap[scope] as? T ?: error("Symbol has no parent of type ${T::class.simpleName}: $this")
	
	
	
	private fun Symbol.beginResolve(): Boolean {
		if(resolved) return true
		if(resolving) error("Circular dependency: ${this.qualifiedName}")
		resolving = true
		return false
	}


	
	private fun resolveType(node: TypeNode): Type {
		val firstType = when {
			node.name != null  -> resolveSymbol(node.name)
			node.names != null -> resolveSymbol(node.names)
			else               -> error("Invalid type node")
		}

		if(firstType !is Type)
			error("Invalid type: $firstType")

		resolveType(firstType)

		val type = if(node.arrayCount != null)
			ArrayType(SymBase.empty(true), firstType, resolveInt(node.arrayCount).toInt())
		else
			firstType

		resolveType(type)
		return type
	}



	private fun resolveType(type: Type) {
		if(type.beginResolve()) return

		when(type) {
			is EnumSymbol    -> resolveEnum(type)
			is TypedefSymbol -> resolveTypedef(type)
			is StructSymbol  -> resolveStruct(type)
			else             -> error("Invalid type: $type")
		}

		type.resolving = false
	}



	private fun resolveConst(symbol: ConstSymbol) {
		val node = symbol.node as ConstNode
		resolveNode(node.value)
		symbol.intValue = resolveInt(node.value)
		symbol.resolved = true
	}



	private fun resolveTypedef(symbol: TypedefSymbol) {
		val node = symbol.node as TypedefNode
		val type = resolveType(node.value)
		symbol.type = type
		symbol.resolved = true
	}



	private fun resolveEnum(enumSymbol: EnumSymbol) {
		pushScope(enumSymbol.thisScope)
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
		popScope()
	}



	private fun resolveStruct(structSymbol: StructSymbol) {
		val structNode = structSymbol.node as StructNode
		var offset = 0
		var maxAlignment = 0

		for(member in structNode.members) {
			val symbol = member.symbol
			symbol.beginResolve()
			val type = resolveType(member.type)
			val size = type.size
			symbol.type = type
			symbol.size = size
			val alignment = size.coerceAtMost(8)
			maxAlignment = max(alignment, maxAlignment)
			offset = (offset + alignment - 1) and -alignment
			symbol.offset = offset
			offset += size
			symbol.intValue = symbol.offset.toLong()
			symbol.resolved = true
		}
		
		structSymbol.size = (offset + maxAlignment - 1) and -maxAlignment
		structSymbol.resolved = true
	}



	/*
	Int resolution
	 */


	
	private fun resolveIntSymbol(symbol: Symbol): Long {
		if(symbol !is IntSymbol) error("Invalid symbol: $symbol")
		if(symbol.beginResolve()) return symbol.intValue

		when(symbol) {
			is ConstSymbol     -> resolveConst(symbol)
			is EnumEntrySymbol -> resolveEnum(symbol.parent<EnumSymbol>())
			is MemberSymbol    -> resolveStruct(symbol.parent<StructSymbol>())
			else               -> error("Invalid symbol: $symbol")
		}

		symbol.resolving = false
		return symbol.intValue
	}



	private fun resolveInt(node: AstNode): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.op.calculate(resolveInt(node.node))
		is BinaryNode -> node.op.calculate(resolveInt(node.left), resolveInt(node.right))
		is SymNode    -> resolveIntSymbol(resolveSymbol(node))
		else          -> error("Invalid node: $node")
	}



}