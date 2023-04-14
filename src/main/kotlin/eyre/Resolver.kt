package eyre

import kotlin.math.max

class Resolver(private val context: CompilerContext) {


	private var scopeStack = Array(64) { Scopes.EMPTY }

	private var scopeStackSize = 0

	private var importStack = Array(64) { ArrayList<Symbol>() }

	private val dotNodes = ArrayList<DotNode>()

	private val refNodes = ArrayList<RefNode>()



	/*
	Scope
	 */



	private fun pushScope(scope: Scope) {
		scopeStack[scopeStackSize++] = scope
	}



	private fun popScope() {
		importStack[scopeStackSize].clear()
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
			for(node in srcFile.nodes)
				resolveNode(node)
			scopeStackSize = prev
		}

		for(node in dotNodes)
			resolveDotNode2(node)

		for(node in context.typeNodes)
			resolveNode2(node)

		for(node in refNodes)
			resolveRef(node)
	}



	/*
	Stage 1: Name resolution
	 */



	/**
	 * Resolves a [name] within the current scope context.
	 */
	private fun resolveName(name: Name): Symbol {
		for(i in scopeStackSize - 1 downTo 0) {
			val scope = scopeStack[i]
			context.symbols.get(scope, name)?.let { return it }
		}

		for(i in scopeStackSize - 1 downTo 0) {
			for(import in importStack[i]) {
				if(import is ScopedSymbol) {
					context.symbols.get(import.thisScope, name)?.let { return it }
				}
			}
		}

		error("Unresolved symbol: $name")
	}



	/**
	 * Resolves a series of [names] within the current scope context.
	 */
	private fun resolveNames(names: Array<Name>): Symbol {
		var symbol = resolveName(names[0])
		for(i in 1 until names.size) {
			if(symbol !is ScopedSymbol) error("Invalid receiver: $symbol")
			symbol = context.symbols.get(symbol.thisScope, names[i]) ?: error("Unresolved symbol: ${names[i]}")
		}
		return symbol
	}



	/*
	Stage 1: Node symbol and type resolution
	 */



	private fun resolveNode(node: AstNode) {
		when(node) {
			is ImportNode ->
				importStack[scopeStackSize].add(resolveNames(node.names))

			is NamespaceNode ->
				pushScope(node.symbol.thisScope)

			is ProcNode -> {
				pushScope(node.symbol.thisScope)
				node.stackNodes.forEach(::resolveNode)
			}

			is ScopeEndNode ->
				popScope()

			is InsNode -> {
				if(node.mnemonic.isPseudo) return
				resolveNode(node.op1 ?: return)
				resolveNode(node.op2 ?: return)
				resolveNode(node.op3 ?: return)
				resolveNode(node.op4 ?: return)
			}

			is VarResNode ->
				node.symbol.type = resolveTypeNode(node.type)

			is VarDbNode -> {
				if(node.type != null) node.symbol.type = resolveTypeNode(node.type)
				for(part in node.parts)
					for(n in part.nodes)
						resolveNode(n)
			}

			is VarAliasNode -> {
				node.symbol.type = resolveTypeNode(node.type)
				resolveNode(node.value)
			}

			is ConstNode ->
				resolveNode(node.value)

			is StructNode ->
				for(member in node.members)
					member.symbol.type = resolveTypeNode(member.type)

			is EnumNode -> {
				pushScope(node.symbol.thisScope)
				for(entry in node.entries)
					entry.value?.let(::resolveNode)
				popScope()
			}

			is TypedefNode ->
				node.symbol.type = resolveTypeNode(node.value)

			is UnaryNode  -> resolveNode(node.node)
			is BinaryNode -> { resolveNode(node.left); resolveNode(node.right) }
			is MemNode    -> resolveNode(node.value)
			is NameNode   -> resolveNameNode(node)
			is DotNode    -> resolveDotNode(node)

			is RefNode    -> {
				resolveNode(node.left)
				refNodes.add(node)
			}

			else -> return
		}
	}



	private fun resolveTypeNode(node: TypeNode): Type {
		var symbol = when {
			node.name != null  -> resolveName(node.name)
			node.names != null -> resolveNames(node.names)
			else               -> error("Invalid type node")
		}

		if(symbol !is Type) error("Invalid type: $symbol")

		symbol = if(node.arrayCount != null)
			ArraySymbol(SymBase(Scopes.EMPTY, Names.EMPTY, true), symbol)
		else
			symbol

		node.symbol = symbol

		return symbol as Type
	}



	private fun resolveDotNode(node: DotNode): Symbol? {
		val receiver: Symbol? = when(node.left) {
			is NameNode -> resolveNameNode(node.left)
			is DotNode  -> resolveDotNode(node.left)
			else        -> error("Invalid receiver: ${node.left}")
		}

		if(receiver !is ScopedSymbol) {
			dotNodes.add(node)
			return null
		}

		if(node.right !is NameNode) error("Invalid node: ${node.right}")

		val symbol = context.symbols.get(receiver.thisScope, node.right.name)
			?: error("Unresolved symbol: ${node.right.name}")

		node.right.symbol = symbol

		return symbol
	}



	private fun resolveNameNode(node: NameNode): Symbol {
		val symbol = resolveName(node.name)
		node.symbol = symbol
		return symbol
	}



	/*
	Stage 2: Dot resolution
	 */



	private fun resolveDotNode2(node: DotNode): Symbol {
		if(node.symbol != null) return node.symbol

		val receiver = node.left.symbol!!

		if(receiver is VarAliasSymbol) {
			val receiverType = receiver.type

			if(receiverType !is ScopedSymbol) error("")

			val invoker = context.symbols.get(
				receiverType.thisScope,
				(node.right as? NameNode)?.name ?: error("Invalid node")
			)

			val symbol = when(invoker) {
				is MemberSymbol -> AliasRefSymbol((receiver.node as VarAliasNode).value, invoker.offset)
				else -> error("Invalid symbol: $invoker")
			}

			node.right.symbol = symbol

			return symbol
		}

		if(receiver !is TypedSymbol || receiver !is PosSymbol) error("Invalid receiver")

		val receiverType = receiver.type

		if(receiverType !is ScopedSymbol) error("")

		val invoker = context.symbols.get(
			receiverType.thisScope,
			(node.right as? NameNode)?.name ?: error("Invalid node")
		)

		val symbol = when(invoker) {
			is MemberSymbol -> RefSymbol(receiver, invoker, invoker.type)
			else -> error("Invalid symbol: $invoker")
		}

		node.right.symbol = symbol

		return symbol
	}



	/*
	Stage 3: Type resolution
	 */



	private fun resolveNode2(node: AstNode) {
		when(node) {
			is ConstNode   -> resolveConst(node.symbol)
			is TypedefNode -> resolveTypedef(node.symbol)
			is EnumNode    -> resolveEnum(node.symbol)
			is StructNode  -> resolveStruct(node.symbol)
			else           -> return
		}
	}



	private inline fun<reified T : ScopedSymbol> Symbol.parent() =
		context.parentMap[scope] as? T ?: error("Symbol has no parent of type ${T::class.simpleName}: $this")



	private fun Symbol.beginResolve(): Boolean {
		if(resolved) return true
		if(resolving) error("Circular dependency: ${this.qualifiedName}")
		resolving = true
		return false
	}



	private fun Symbol.endResolve() {
		resolving = false
		resolved = true
	}



	private fun resolveType(type: Type) {
		when(type) {
			is ArraySymbol   -> resolveType(type.type)
			is EnumSymbol    -> resolveEnum(type)
			is TypedefSymbol -> resolveTypedef(type)
			is StructSymbol  -> resolveStruct(type)
		}
	}




	private fun resolveConst(symbol: ConstSymbol) {
		if(symbol.beginResolve()) return
		val node = symbol.node as ConstNode
		symbol.intValue = resolveInt(node.value)
		symbol.endResolve()
	}



	private fun resolveTypedef(symbol: TypedefSymbol) {
		if(symbol.beginResolve()) return
		resolveType(symbol.type)
		symbol.endResolve()
	}



	private fun resolveEnum(enumSymbol: EnumSymbol) {
		if(enumSymbol.beginResolve()) return
		pushScope(enumSymbol.thisScope)
		val enumNode = enumSymbol.node as EnumNode
		var current = if(enumSymbol.isBitmask) 1L else 0L
		var max = 0L

		for(node in enumNode.entries) {
			val symbol = node.symbol

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
			symbol.resolved = true
		}

		enumSymbol.size = when {
			max.isImm8  -> 1
			max.isImm16 -> 2
			max.isImm32 -> 4
			else        -> 8
		}

		popScope()
		enumSymbol.endResolve()
	}



	private fun resolveStruct(structSymbol: StructSymbol) {
		if(structSymbol.beginResolve()) return
		val structNode = structSymbol.node as StructNode
		var offset = 0
		var maxAlignment = 0

		for(member in structNode.members) {
			val symbol = member.symbol
			val type = symbol.type
			resolveType(type)
			val size = type.size
			symbol.type = type
			symbol.size = size
			val alignment = type.alignment.coerceAtMost(8)
			maxAlignment = max(alignment, maxAlignment)
			offset = (offset + alignment - 1) and -alignment
			symbol.offset = offset
			offset += size
			symbol.intValue = symbol.offset.toLong()
			symbol.resolved = true
		}

		structSymbol.size = (offset + maxAlignment - 1) and -maxAlignment
		structSymbol.alignment = maxAlignment
		structSymbol.endResolve()
	}



	/*
	Stage 3: Integer calculation
	 */



	private fun resolveIntSymbol(symbol: Symbol): Long {
		if(symbol !is IntSymbol) error("Invalid symbol: $symbol")

		when(symbol) {
			is ConstSymbol     -> resolveConst(symbol)
			is EnumEntrySymbol -> if(!symbol.resolved) resolveEnum(symbol.parent<EnumSymbol>())
			is MemberSymbol    -> if(!symbol.resolved) resolveStruct(symbol.parent<StructSymbol>())
			else               -> error("Invalid symbol: $symbol")
		}

		return symbol.intValue
	}



	private fun resolveInt(node: AstNode): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.op.calculate(resolveInt(node.node))
		is BinaryNode -> node.op.calculate(resolveInt(node.left), resolveInt(node.right))
		is DotNode    -> resolveIntSymbol(resolveDotNode2(node))
		is SymNode    -> resolveIntSymbol(node.symbol!!)
		else          -> error("Invalid node: $node")
	}



	/*
	Stage 4
	 */



	private fun resolveRef(node: RefNode) {
		val left = node.left.symbol

		node.right.symbol = when(node.right.name) {
			Names.SIZE -> when(left) {
				is VarDbSymbol  -> IntSymbol(SymBase.EMPTY, left.size.toLong())
				is VarResSymbol -> IntSymbol(SymBase.EMPTY, left.type.size.toLong())
				is Type         -> IntSymbol(SymBase.EMPTY, left.size.toLong())
				else            -> error("Invalid reference")
			}

			Names.COUNT -> when(left) {
				is EnumSymbol -> IntSymbol(SymBase.EMPTY, left.entries.size.toLong())
				else -> error("Invalid reference")
			}

			else -> error("Invalid reference")
		}
	}


}