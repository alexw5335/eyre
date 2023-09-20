package eyre

class Resolver(private val context: CompilerContext) {


    private var scopeStack = Array(64) { Scopes.EMPTY }

    private var scopeStackSize = 0

    private var importStack = Array(64) { ArrayList<Symbol>() }



    private fun pushScope(scope: Scope) {
        scopeStack[scopeStackSize++] = scope
    }

    private fun popScope() {
        importStack[scopeStackSize].clear()
        scopeStackSize--
    }

	private fun err(srcPos: SrcPos?, message: String): Nothing {
		context.err(srcPos, message)
	}



    fun resolve() {
        pushScope(Scopes.EMPTY)
		scopeStackSize = 1

		for(srcFile in context.srcFiles) {
			try {
				resolveFile(srcFile)
			} catch(_: EyreException) {
				srcFile.invalid = true
			}
		}

		popScope()
    }



	private fun resolveFile(srcFile: SrcFile) {
		if(srcFile.resolved)
			return

		if(srcFile.resolving)
			err(null, "Cyclic compile-time file dependency")

		srcFile.resolving = true

		for(node in srcFile.nodes)
			determineTypes(node)

		for(node in srcFile.nodes)
			resolveNode(node)

		srcFile.resolving = false
		srcFile.resolved = true
	}



	/*
	Name resolution
	 */



	/**
	 * Resolves a [name] within the current scope context.
	 */
	private fun resolveName(srcPos: SrcPos?, name: Name): Symbol {
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

		err(srcPos, "Unresolved symbol: $name")
	}



	/**
	 * Resolves a series of [names] within the current scope context.
	 */
	private fun resolveNames(srcPos: SrcPos?, names: Array<Name>): Symbol {
		var symbol = resolveName(srcPos, names[0])

		for(i in 1 ..< names.size) {
			if(symbol !is ScopedSymbol)
				err(srcPos, "Invalid receiver: ${symbol.name}")

			symbol = context.symbols.get(symbol.thisScope, names[i]) ?: error("Unresolved symbol: ${names[i]}")
		}

		return symbol
	}



	/*
	Stage 1: Type resolution - partial visit
	 */



	private fun determineTypes(node: AstNode) { when(node) {
		is Namespace -> pushScope(node.thisScope)
		is Proc      -> pushScope(node.thisScope)
		is ScopeEnd  -> popScope()
		is VarDb     -> if(node.typeNode != null) node.type = determineTypeNode(node.typeNode)
		is VarRes    -> if(node.typeNode != null) node.type = determineTypeNode(node.typeNode)
		is Struct    -> for(member in node.members) member.type = determineTypeNode(member.typeNode)
		is Typedef   -> determineTypeNode(node.typeNode)
		else         -> { }
	}}



	private fun determineTypeNode(node: TypeNode): Type {
		val symbol = when {
			node.name != null  -> resolveName(node.srcPos, node.name)
			node.names != null -> resolveNames(node.srcPos, node.names)
			else               -> error("Invalid type node")
		}

		var type = symbol as? Type ?: err(node.srcPos, "Expecting type, found: ${symbol.qualifiedName}")

		if(node.arraySizes != null)
			for(n in node.arraySizes)
				type = ArrayType(type)

		node.type = type

		return type
	}



	/*
	Stage 2: Symbol resolution and constant calculation - full visit
	 */



	private fun resolveInt(node: AstNode): Long {
		fun sym(symbol: Symbol): Long {
			if(!symbol.resolved)
				Resolver(context).resolveFile(node.srcPos!!.file)

			if(symbol.resolving)
				err(node.srcPos, "Cyclic compile-time constant dependency")

			if(symbol is IntSymbol)
				return symbol.intValue

			err(node.srcPos, "Invalid integer constant: ${symbol.qualifiedName}")
		}

		return when(node) {
			is IntNode -> node.value
			is UnaryNode -> node.calculate(::resolveInt)
			is NameNode -> {
				val symbol = resolveName(node.srcPos, node.value)
				node.symbol = symbol
				sym(symbol)
			}
			is BinaryNode -> {
				when(node.op) {
					BinaryOp.DOT -> {
						resolveNode(node.left)
						resolveDotNode(node)
						sym(node.symbol!!)
					}
					BinaryOp.REF -> 0L
					else -> node.calculate(::resolveInt)
				}
			}
			else -> err(node.srcPos, "Invalid integer constant node: $node")
		}
	}



	private fun resolveNode(node: AstNode) { when(node) {
		is Namespace -> pushScope(node.thisScope)
		is Proc      -> pushScope(node.thisScope)
		is ScopeEnd  -> popScope()

		is NameNode -> {
			node.symbol = resolveName(node.srcPos, node.value)
		}

		is Const -> node.resolve {
			intValue = resolveInt(valueNode)
		}

		is InsNode -> {
			if(node.mnemonic.type == Mnemonic.Type.PSEUDO) return
			if(node.op1.node != NullNode) resolveNode(node.op1.node)
			if(node.op2.node != NullNode) resolveNode(node.op2.node)
			if(node.op3.node != NullNode) resolveNode(node.op3.node)
			if(node.op4.node != NullNode) resolveNode(node.op4.node)
		}

		is Enum -> node.resolve(::resolveEnum)

		is Struct -> node.resolve(::resolveStruct)

		is VarRes -> {
			node.typeNode?.let(::resolveTypeNode)
		}

		is VarDb -> {
			node.typeNode?.let(::resolveTypeNode)
			for(part in node.parts)
				part.nodes.forEach(::resolveNode)
		}

		is Typedef -> {
			node.typeNode.let(::resolveTypeNode)
		}

		is UnaryNode -> {
			resolveNode(node.node)
		}

		is BinaryNode -> {
			resolveNode(node.left)

			when(node.op) {
				BinaryOp.DOT -> resolveDotNode(node)
				BinaryOp.REF -> { }
				else         -> resolveNode(node.right)
			}
		}

		is RegNode,
		is StringNode,
		is IntNode,
		is Label,
		is FloatNode -> Unit

		is TypeNode,
		NullNode,
		is Member,
		is EnumEntry,
		is OpNode -> context.internalError("Invalid node: $node")
	}}



	private fun resolveTypeNode(node: TypeNode): Type {
		val type = node.type!!
		val arraySizes = node.arraySizes ?: return type
		var array = type as ArrayType

		for(n in arraySizes) {
			val count = resolveInt(n)
			if(count < 0 || count >= Int.MAX_VALUE)
				err(node.srcPos, "Invalid array size: $count")
			array.count = count.toInt()
			array = array.type as? ArrayType ?: break
		}

		return type
	}



	private fun resolveDotNode(node: BinaryNode): Symbol {
		val receiver = when(node.left) {
			is NameNode   -> node.left.symbol
			is BinaryNode -> node.left.symbol
			else          -> err(node.left.srcPos, "Invalid receiver")
		}

		val right = node.right

		if(receiver !is ScopedSymbol)
			err(node.left.srcPos, "Invalid receiver (not scoped): $receiver")

		if(right !is NameNode)
			err(right.srcPos, "Invalid operand: ${right::class.simpleName}")

		val symbol = context.symbols.get(receiver.thisScope, right.value)
			?: err(node.srcPos, "Unresolved reference: ${right.value}")

		right.symbol = symbol
		node.symbol = symbol
		return symbol
	}



	private inline fun<T> T.resolve(block: T.() -> Unit) where T : Symbol, T : AstNode {
		resolving = true
		block()
		resolved = true
		resolving = false
	}



	private fun resolveStruct(struct: Struct) {
		var offset = 0
		var maxAlignment = 0

		for(member in struct.members) {
			member.resolve {
				member.type = resolveTypeNode(member.typeNode)
				val alignment = member.type.alignment.coerceAtMost(8)
				maxAlignment = maxAlignment.coerceAtLeast(alignment)
				member.offset = (offset + alignment - 1) and -alignment
				offset += member.type.size
			}
		}

		struct.size = (offset + maxAlignment - 1) and -maxAlignment
		struct.alignment = maxAlignment
	}



	private fun resolveEnum(enum: Enum) {
		pushScope(enum.thisScope)
		var current = if(enum.isBitmask) 1L else 0L
		var max = 0L

		for(entry in enum.entries) {
			entry.resolve {
				if(entry.valueNode == null) {
					entry.value = current
					current += if(enum.isBitmask) current else 1
				} else {
					entry.value = resolveInt(entry.valueNode)
					current = entry.value
					if(enum.isBitmask && current.countOneBits() != 1)
						err(entry.srcPos, "Bitmask entry must be power of two")
				}

				max = max.coerceAtLeast(entry.value)
			}
		}

		enum.size = when {
			max.isImm8  -> 1
			max.isImm16 -> 2
			max.isImm32 -> 4
			else        -> 8
		}

		popScope()
	}


}