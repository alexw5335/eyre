package eyre

import java.lang.RuntimeException

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



    fun resolve() {
        pushScope(Scopes.EMPTY)

		scopeStackSize = 1

        for(srcFile in context.srcFiles) {
			try {
				for(node in srcFile.nodes)
					resolveNode(node)
			} catch(_: ResolverException) {
				srcFile.invalid = true
			}
        }
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

		err(srcPos, "Unresoled symbol: $name")
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
    Stage 1: Symbol resolution
     */



    private fun resolveNode(node: AstNode) { when(node) {
        is Namespace  -> pushScope(node.thisScope)
        is Proc       -> pushScope(node.thisScope)
        is ScopeEnd   -> popScope()
        is UnaryNode  -> resolveNode(node.node)
		is NameNode   -> node.symbol = resolveName(node.srcPos, node.value)

		// String literals in OpNodes are resolved in the Assembler for convenience
        is OpNode -> resolveNode(node.node)

		is BinaryNode -> when(node.op) {
			BinaryOp.DOT -> resolveDotNode(node)
			BinaryOp.REF -> err(node.srcPos, "REF not yet supported")
			else         -> { resolveNode(node.left); resolveNode(node.right) }
		}

        is InsNode -> {
            if(node.mnemonic.type == Mnemonic.Type.PSEUDO) return
            resolveNode(node.op1)
            resolveNode(node.op2)
            resolveNode(node.op3)
            resolveNode(node.op4)
        }

        is Enum -> {
            pushScope(node.thisScope)
            for(entry in node.entries)
                entry.valueNode?.let(::resolveNode)
            popScope()
        }

		is TypeNode  -> resolveTypeNode(node)
		is EnumEntry -> node.valueNode?.let(::resolveNode)
		is VarDbNode -> for(part in node.parts) part.nodes.forEach(::resolveNode)
		is Typedef   -> resolveTypeNode(node.typeNode)

		// is StringNode -> node.symbol = context.addStringLiteral(node.value)
		is StringNode,
		is DbPart,
		is FloatNode,
		is IntNode,
		is Label,
		NullNode,
		is RegNode -> { }
	}}



	private fun resolveDotNode(node: BinaryNode) {
		resolveNode(node.left)

		val left = node.left
		val right = node.right

		if(left !is NameNode)
			err(node.left.srcPos, "Invalid receiver: $left")

		val receiver = left.symbol

		if(receiver !is ScopedSymbol)
			err(node.left.srcPos, "Invalid receiver: $receiver")

		if(right !is NameNode)
			err(node.right.srcPos, "Invalid operand: ${right::class.simpleName}")

		right.symbol = context.symbols.get(receiver.thisScope, right.value)
			?: err(node.srcPos, "Unresolved reference: ${right.value}")
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


}