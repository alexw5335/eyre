package eyre

import kotlin.math.max

class Resolver(private val context: CompilerContext) {


	private var scopeStack = Array(64) { Scopes.EMPTY }

	private var scopeStackSize = 0

	private var importStack = Array(64) { ArrayList<Symbol>() }



	/*
	Scope
	 */



	private fun ScopedSymbol.child(name: Name) = context.symbols.get(thisScope, name)

	private fun Scope.child(name: Name) = context.symbols.get(this, name)



	private fun pushScope(scope: Scope) {
		scopeStack[scopeStackSize++] = scope
	}



	private fun popScope() {
		importStack[scopeStackSize].clear()
		scopeStackSize--
	}



	private fun addImport(import: Array<Name>) {
		importStack[scopeStackSize - 1].add(resolveNames(import))
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

		for(node in context.unorderedNodes)
			calculateNode(node)
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
	Stage 1: Symbol resolution
	 */



	private fun resolveNode(node: AstNode) { when(node) {
		is ImportNode -> addImport(node.names)
		is NamespaceNode -> pushScope(node.symbol.thisScope)
		is ProcNode -> {
			pushScope(node.symbol.thisScope)
			node.stackNodes.forEach(::resolveNode)
		}
		is ScopeEndNode -> popScope()
		is InsNode -> {
			if(node.mnemonic == Mnemonic.DLLCALL || node.mnemonic == Mnemonic.RETURN)
				return
			resolveNode(node.op1)
			resolveNode(node.op2)
			resolveNode(node.op3)
			resolveNode(node.op4)
		}
		is ConstNode -> resolveNode(node.value)
		is StructNode ->
			for(member in node.members)
				member.symbol.type = resolveTypeNode(member.type)
		is EnumNode -> {
			pushScope(node.symbol.thisScope)
			for(entry in node.entries)
				entry.value?.let(::resolveNode)
			popScope()
		}
		is TypedefNode -> node.symbol.type = resolveTypeNode(node.value)
		is UnaryNode  -> resolveNode(node.node)
		is BinaryNode -> { resolveNode(node.left); resolveNode(node.right) }
		is OpNode     -> {
			if(node.node is StringNode)
				node.node.symbol = context.addStringLiteral(node.node.value)
			else
				resolveNode(node.node)
		}
		is NameNode   -> resolveNameNode(node)
		is DotNode    -> resolveDotNode(node)
		is RefNode -> {
			resolveNode(node.left)
			context.unorderedNodes.add(node)
		}
		is ArrayNode -> {
			resolveNode(node.receiver)
			resolveNode(node.index)
			context.unorderedNodes.add(node)
		}

		is VarResNode ->
			node.symbol.type = resolveTypeNode(node.type)
		is VarDbNode -> {
			node.symbol.type = node.type?.let(::resolveTypeNode) ?: VoidType
			for(part in node.parts)
				for(n in part.nodes)
					resolveNode(n)
		}
		is VarAliasNode -> {
			node.symbol.type = resolveTypeNode(node.type)
			resolveNode(node.value)
		}
		is VarInitNode -> {
			node.symbol.type = resolveTypeNode(node.type)
			if(node.initialiser is InitNode)
				node.initialiser.type = node.symbol.type
			resolveNode(node.initialiser)
		}
		is InitNode -> {
			context.unorderedNodes.add(node)
			for(n in node.entries)
				if(n.node is EqualsNode)
					resolveNode(n.node.right)
				else
					resolveNode(n.node)
		}

		is IndexNode -> resolveNode(node.index)

		else -> return
	}}



	private fun resolveTypeNode(node: TypeNode): Type {
		var symbol = when {
			node.name != null  -> resolveName(node.name)
			node.names != null -> resolveNames(node.names)
			else               -> error("Invalid type node")
		}

		if(symbol !is Type) error("Invalid type: $symbol")

		if(node.arraySizes != null) {
			for(n in node.arraySizes)
				symbol = ArraySymbol(SymBase(Scopes.EMPTY, Names.EMPTY), symbol as Type)
			context.unorderedNodes.add(node)
		}

		node.symbol = symbol

		return symbol as Type
	}



	private fun resolveNameNode(node: NameNode): Symbol {
		val symbol = resolveName(node.name)
		node.symbol = symbol
		return symbol
	}



	private fun resolveDotNode(node: DotNode): Symbol? {
		val receiver: Symbol? = when(val node2 = node.left) {
			is NameNode  -> resolveNameNode(node2)
			is DotNode   -> resolveDotNode(node2)
			is ArrayNode -> { resolveNode(node2); context.unorderedNodes.add(node); return null }
			else         -> error("Invalid receiver: ${node.left}")
		}

		if(receiver !is ScopedSymbol) {
			context.unorderedNodes.add(node)
			return null
		}

		if(node.right !is NameNode) error("Invalid node: ${node.right}")

		val symbol = context.symbols.get(receiver.thisScope, node.right.name)
			?: error("Unresolved symbol: ${node.right.name}")

		node.right.symbol = symbol

		return symbol
	}



	/*
	Stage 2: Constant calculation
	 */



	private fun calculateNode(node: AstNode) { when(node) {
		is ConstNode   -> calculateConst(node.symbol)
		is TypedefNode -> calculateTypedef(node.symbol)
		is EnumNode    -> calculateEnum(node.symbol)
		is StructNode  -> calculateStruct(node.symbol)
		is RefNode     -> calculateRefNode(node)
		is DotNode     -> calculateDotNode(node)
		is NameNode    -> return
		is TypeNode    -> calculateTypeNode(node)
		is ArrayNode   -> calculateArrayNode(node)
		is InitNode    -> calculateInitNode(node)
		else           -> error("Invalid node: $node")
	}}



	private var initOffset = 0



	private fun error(): Nothing = error("Resolver error")



	private fun calculateEquals(node: AstNode, initialType: Type): Type {
		if(node is IndexNode) {
			if(initialType !is ArraySymbol) error()
			val index = calculateInt(node.index).toInt()
			if(index < 0 || index >= initialType.count) error()
			initOffset += index * initialType.type.size
			return initialType.type
		}

		if(node is NameNode) {
			if(initialType !is StructSymbol) error()
			val member = context.symbols.get(initialType.thisScope, node.name)
				as? MemberSymbol ?: error()
			initOffset += member.offset
			return member.type
		}

		if(node is DotNode)
			return calculateEquals(node.right, calculateEquals(node.left, initialType))

		if(node is ArrayNode) {
			val type = calculateEquals(node.receiver, initialType)
			if(type !is ArraySymbol) error()
			val index = calculateInt(node.index).toInt()
			if(index < 0 || index >= type.count) error()
			initOffset += index * type.size
			return type
		}

		error("Invalid node: $node")
	}



	@Suppress("CascadeIf")
	private fun calculateInitNode(node: InitNode) {
		val type = node.type
		val nodes = node.entries
		var hasEquals = false

		if(type is ArraySymbol) {
			for((i, entry) in nodes.withIndex()) {
				if(entry.node is EqualsNode) {
					hasEquals = true
					initOffset = 0
					entry.type = calculateEquals(entry.node.left, type)
					entry.offset = initOffset
					if(entry.node.right is InitNode) entry.node.right.type = type.type
				} else if(hasEquals) {
					error("Indexed array initialisers must occur after un-indexed ones")
				} else {
					if(i >= type.count) error()
					entry.type = type.type
					entry.offset = i * type.type.size
					if(entry.node is InitNode) entry.node.type = type.type
				}
			}
		} else if(type is StructSymbol) {
			for((i, entry) in nodes.withIndex()) {
				if(entry.node is EqualsNode) {
					hasEquals = true
					initOffset = 0
					entry.type = calculateEquals(entry.node.left, type)
					entry.offset = initOffset
					if(entry.node.right is InitNode) entry.node.right.type = entry.type
				} else if(hasEquals) {
					error("Named struct initialisers must occur after unnamed ones")
				} else {
					if(i >= type.members.size) error()
					entry.type = type.members[i].type
					entry.offset = type.members[i].offset
					if(entry.node is InitNode) entry.node.type = entry.type
				}
			}
		} else {
			error("Invalid receiver: $type")
		}
	}



	private inline fun<reified T : ScopedSymbol> Symbol.parent() =
		context.parentMap[scope] as? T ?: error("Symbol has no parent of type ${T::class.simpleName}: $this")



	private fun Symbol.begin(): Boolean {
		if(resolved) return true
		if(resolving) error("Circular dependency: ${this.qualifiedName}")
		resolving = true
		return false
	}



	private fun Symbol.end() {
		resolving = false
		resolved = true
	}



	private fun calculateSymbol(symbol: Symbol) {
		when(symbol) {
			is EnumEntrySymbol -> if(!symbol.resolved) calculateEnum(symbol.parent())
			is MemberSymbol    -> if(!symbol.resolved) calculateStruct(symbol.parent())
			is ArraySymbol     -> calculateSymbol(symbol.type)
			is ConstSymbol     -> calculateConst(symbol)
			is TypedefSymbol   -> calculateTypedef(symbol)
			is EnumSymbol      -> calculateEnum(symbol)
			is StructSymbol    -> calculateStruct(symbol)
		}
	}



	private fun calculateConst(symbol: ConstSymbol) {
		if(symbol.begin()) return
		val node = symbol.node as ConstNode
		symbol.intValue = calculateInt(node.value)
		symbol.end()
	}



	private fun calculateTypedef(symbol: TypedefSymbol) {
		if(symbol.begin()) return
		calculateSymbol(symbol.type)
		symbol.end()
	}



	private fun calculateEnum(enumSymbol: EnumSymbol) {
		if(enumSymbol.begin()) return
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
				symbol.intValue = calculateInt(node.value)
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
		enumSymbol.end()
	}



	private fun calculateStruct(structSymbol: StructSymbol) {
		if(structSymbol.begin()) return
		val structNode = structSymbol.node as StructNode
		var offset = 0
		var maxAlignment = 0

		for(member in structNode.members) {
			val symbol = member.symbol
			val type = symbol.type
			calculateSymbol(type)
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
		structSymbol.end()
	}



	private fun calculateRefNode(node: RefNode): Symbol {
		if(node.left.symbol == null)
			calculateNode(node.left)
		val left = node.left.symbol!!
		calculateSymbol(left)

		node.right.symbol = when(node.right.name) {
			// Unnecessary inclusion of string literal into executable
			Names.INT -> when(left) {
				is StringLiteralSymbol -> IntSymbol(left.string.ascii64())
				else -> error("Invalid reference")
			}

			Names.SIZE -> when(left) {
				is VarDbSymbol  -> IntSymbol(left.size)
				is VarResSymbol -> IntSymbol(left.type.size)
				is Type         -> IntSymbol(left.size)
				else            -> error("Invalid reference")
			}

			Names.COUNT -> when(left) {
				is EnumSymbol -> IntSymbol(left.entries.size)
				is ArraySymbol -> IntSymbol(left.count)
				is TypedSymbol ->
					IntSymbol((left.type as? ArraySymbol)?.count ?: error("Invalid reference"))
				else -> error("Invalid reference")
			}

			else -> error("Invalid reference")
		}

		return node.right.symbol!!
	}



	private fun calculateDotNode(node: DotNode): Symbol {
		if(node.symbol != null)
			return node.symbol

		val receiver = node.left.symbol as? TypedSymbol ?: error("Invalid receiver")
		val receiverType = receiver.type as? ScopedSymbol ?: error("Invalid receiver")
		val invoker = context.symbols.get(
			receiverType.thisScope,
			(node.right as? NameNode)?.name ?: error("Invalid node")
		) as? MemberSymbol ?: error("Expecting struct member")

		node.right.symbol = when(receiver) {
			is VarAliasSymbol -> {
				AliasRefSymbol((receiver.node as VarAliasNode).value, invoker.offset)
			}
			is PosSymbol -> {
				calculateSymbol(invoker)
				PosRefSymbol(receiver, invoker.offset, invoker.type)
			}
			else -> {
				error("Invalid receiver")
			}
		}

		return node.right.symbol!!
	}



	private fun calculateTypeNode(node: TypeNode) {
		val symbol = node.symbol!!
		if(symbol.begin()) return
		if(node.arraySizes != null) {
			var array = symbol as ArraySymbol
			for(n in node.arraySizes) {
				array.count = calculateInt(n).toInt()
				array = array.type as? ArraySymbol ?: break
			}
		}
		symbol.end()
	}



	private fun calculateArrayNode(node: ArrayNode): Symbol {
		calculateNode(node.receiver)
		val receiver = node.receiver.symbol!!
		calculateSymbol(receiver)
		if(receiver !is TypedSymbol) error("Invalid receiver: $receiver")
		val type = receiver.type
		if(type !is ArraySymbol) error("Invalid receiver, expecting array: $receiver")

		if(receiver is PosSymbol) {
			val offset = type.type.size * calculateInt(node.index).toInt()
			node.symbol = PosRefSymbol(receiver, offset, type.type)
			return node.symbol!!
		}

		error("Invalid receiver: $receiver")
	}



	private fun calculateIntSymbol(symbol: Symbol): Long {
		calculateSymbol(symbol)
		if(symbol !is IntSymbol) error("Invalid symbol")
		return symbol.intValue
	}



	private fun calculateInt(node: AstNode): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.op.calculate(calculateInt(node.node))
		is BinaryNode -> node.op.calculate(calculateInt(node.left), calculateInt(node.right))
		is RefNode    -> calculateIntSymbol(calculateRefNode(node))
		is DotNode    -> calculateIntSymbol(calculateDotNode(node))
		is NameNode   -> calculateIntSymbol(node.symbol!!)
		else          -> error("Invalid node: $node")
	}


}