package eyre

import java.lang.RuntimeException

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

    //private fun addImport(import: Array<Name>) {
    //    importStack[scopeStackSize - 1].add(resolveNames(import))
    //}

	private fun resolverError(srcPos: SrcPos, message: String): Nothing {
		context.errors.add(EyreError(srcPos, message))
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
    private fun resolveName(name: Name): Symbol? {
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

		return null
    }



   // /**
    // * Resolves a series of [names] within the current scope context.
    // */
/*    private fun resolveNames(names: Array<Name>): Symbol {
        var symbol = resolveName(names[0])
        for(i in 1 ..< names.size) {
            if(symbol !is ScopedSymbol) error("Invalid receiver: $symbol")
            symbol = context.symbols.get(symbol.thisScope, names[i]) ?: error("Unresolved symbol: ${names[i]}")
        }
        return symbol
    }*/



    /*
    Stage 1: Symbol resolution
     */



    private fun resolveNode(node: AstNode) { when(node) {
        is Namespace  -> pushScope(node.thisScope)
        is Proc       -> pushScope(node.thisScope)
        is ScopeEnd   -> popScope()
        is UnaryNode  -> resolveNode(node.node)
        is OpNode     -> resolveNode(node.node)
        is NameNode   -> resolveNameNode(node)

		is BinaryNode -> {
			resolveNode(node.left)

			if(node.op == BinaryOp.DOT) {
				//
			} else if(node.op == BinaryOp.REF) {
				//
			} else {
				resolveNode(node.right)
			}
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

        else -> return
    }}



    private fun resolveNameNode(node: NameNode): Symbol {
        val symbol = resolveName(node.value)
			?: resolverError(node.srcPos ?: context.internalError(), "Unresolved reference: ${node.value}")
        node.symbol = symbol
        return symbol
    }



/*	private fun resolveDotNode(node: DotNode): Symbol {
		val receiver: Symbol = when(val left = node.left) {
			is NameNode  -> resolveNameNode(left)
			is DotNode   -> resolveDotNode(left)
			else         -> error("Invalid receiver: $left")
		}

		if(node.right !is NameNode)
			error("Invalid node: ${node.right}")

		if(receiver !is ScopedSymbol)
			resolverError(node.srcPos ?: context.internalError(), "Invalid receiver: ${receiver.qualifiedName}")

		val symbol = context.symbols.get(receiver.thisScope, node.right.value)
			?: resolverError(node.srcPos ?: context.internalError(), "Unresolved reference: ${node.right.value}")

		node.right.symbol = symbol

		return symbol
	}*/


}