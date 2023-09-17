package eyre

import java.lang.RuntimeException
import kotlin.math.max

/**
 * The resolver runs in three stages:
 *
 * 1. References to most symbols are resolved
 * 2. References to members of types are resolved
 * 3. Constants values are calculated (array sizes, constants, enum values, etc.)
 *
 * The first stage is ordered (recursive descent). The second and third stages are
 * unordered.
 */
class Resolver(private val context: CompilerContext) {


	private class ResolverException : RuntimeException()

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
		context.errors.add(EyreException(srcPos, message))
		throw ResolverException()
	}



	private inline fun visit(visitor: (AstNode) -> Unit) {
		for(srcFile in context.srcFiles) {
			try {
				for(node in srcFile.nodes)
					visitor(node)
			} catch(_: ResolverException) {
				srcFile.invalid = true
			}
		}
	}



    fun resolve() {
        pushScope(Scopes.EMPTY)

		scopeStackSize = 1

		visit(::resolveTypes)
		visit(::resolveNode)

		for(node in context.unorderedNodes)
			calculateNode(node)
    }



    /*
	Stage 1: Name resolution
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
	Stage 1: Type resolution
	 */



	private fun resolveTypes(node: AstNode) { when(node) {
		is Namespace -> pushScope(node.thisScope)
		is Proc      -> pushScope(node.thisScope)
		is ScopeEnd  -> popScope()
		is VarDb     -> if(node.typeNode != null) node.type = resolveTypeNode(node.typeNode)
		is VarRes    -> if(node.typeNode != null) node.type = resolveTypeNode(node.typeNode)
		is Struct    -> for(member in node.members) member.type = resolveTypeNode(member.typeNode)
		else         -> { }
	}}



    /*
    Stage 1: Symbol resolution
     */



	private fun resolveExpr(node: AstNode) { when(node) {
		is UnaryNode -> resolveExpr(node.node)
		is NameNode  -> node.symbol = resolveName(node.srcPos, node.value)

		is BinaryNode -> {
			resolveExpr(node.left)

			when(node.op) {
				BinaryOp.DOT -> resolveDotNode(node)
				BinaryOp.REF -> err(node.srcPos, "Ref nodes unsupported")
				else         -> resolveExpr(node.right)
			}
		}

		is IntNode,
		is FloatNode,
		is StringNode -> Unit

		else -> err(node.srcPos, "Invalid expression node: $node")
	}}



    private fun resolveNode(node: AstNode) { when(node) {
		is Namespace -> pushScope(node.thisScope)
		is Proc      -> pushScope(node.thisScope)
		is ScopeEnd  -> popScope()

		is Const     -> resolveExpr(node.valueNode)
		is NameNode  -> node.symbol = resolveName(node.srcPos, node.value)
		is TypeNode  -> resolveTypeNode(node)
		is VarDb     -> for(part in node.parts) part.nodes.forEach(::resolveExpr)
		is Typedef   -> resolveTypeNode(node.typeNode)

        is InsNode -> {
            if(node.mnemonic.type == Mnemonic.Type.PSEUDO) return
			if(node.op1.node != NullNode) resolveExpr(node.op1.node)
			if(node.op2.node != NullNode) resolveExpr(node.op2.node)
			if(node.op3.node != NullNode) resolveExpr(node.op3.node)
			if(node.op4.node != NullNode) resolveExpr(node.op4.node)
        }

        is Enum -> {
            pushScope(node.thisScope)
            for(entry in node.entries)
                entry.valueNode?.let(::resolveExpr)
            popScope()
        }

		NullNode,
		is VarRes,
		is StringNode,
		is FloatNode,
		is IntNode,
		is Label,
		is RegNode,
		is EnumEntry,
		is UnaryNode,
		is BinaryNode,
		is Struct,
		is OpNode -> context.internalError("Invalid node: $node")
	}}



	private fun resolveDotNode(node: BinaryNode): Symbol {
		val receiver = (node.left as? SymHolder)?.symbol
			?: err(node.left.srcPos, "Invalid '.' operand")

		val right = node.right

		if(receiver !is ScopedSymbol)
			err(node.left.srcPos, "Invalid receiver: $receiver")

		if(right !is NameNode)
			err(right.srcPos, "Invalid operand: ${right::class.simpleName}")

		val symbol = context.symbols.get(receiver.thisScope, right.value)
			?: err(node.srcPos, "Unresolved reference: ${right.value}")

		right.symbol = symbol
		node.symbol = symbol
		return symbol
	}



	private fun resolveTypeNode(node: TypeNode): Type {
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
	Stage 2: Constant calculation
	 */



	private fun calculateInt(node: AstNode): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.op.calculate(calculateInt(node.node))
		is BinaryNode -> node.op.calculate(calculateInt(node.left), calculateInt(node.right))
		is NameNode   -> calculateIntSymbol(node.symbol ?: error("Invalid symbol"))
		else          -> error("Invalid node: $node")
	}



	private fun calculateIntSymbol(symbol: Symbol): Long {
		if(symbol !is AstNode || symbol !is IntSymbol) error("Invalid symbol")
		calculateNode(symbol)
		return symbol.intValue
	}



	private fun calculateNode(node: AstNode) {
		if(node !is Symbol) return

		if(node.begin()) return

		when(node) {
			is Const -> node.intValue = calculateInt(node.valueNode)

			is Struct -> {

			}

			else -> err(node.srcPos, "Invalid node?")
		}

		node.end()
	}


	private fun calculateStruct(struct: Struct) {
		if(struct.begin()) return
		var offset = 0
		var maxAlignment = 0

		for(member in struct.members) {
			val type = member.type
			calculateNode(type as AstNode)
			val size = type.size
			member.size = size
			val alignment = type.alignment.coerceAtMost(8)
			maxAlignment = max(alignment, maxAlignment)
			offset = (offset + alignment - 1) and -alignment
			member.offset = offset
			offset += size
			member.intValue = symbol.offset.toLong()
			member.resolved = true
		}

		structSymbol.size = (offset + maxAlignment - 1) and -maxAlignment
		structSymbol.alignment = maxAlignment
		structSymbol.end()
	}


	private fun<T> T.begin(): Boolean where T : AstNode, T : Symbol {
		if(resolved) return true
		if(resolving) error("Circular dependency: ${this.qualifiedName}")
		resolving = true
		return false
	}



	private fun<T> T.end() where T : AstNode, T : Symbol{
		resolving = false
		resolved = true
	}


}