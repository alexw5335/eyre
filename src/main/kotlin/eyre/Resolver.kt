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

    private fun addImport(import: Array<Name>) {
        importStack[scopeStackSize - 1].add(resolveNames(import))
    }



    fun resolve() {
        pushScope(Scopes.EMPTY)

        for(srcFile in context.srcFiles) {
            val prev = scopeStackSize
            scopeStackSize = 1
            for(node in srcFile.nodes)
                resolveNode(node)
            scopeStackSize = prev
        }

        //for(node in context.unorderedNodes)
        //    calculateNode(node)
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
        for(i in 1 ..< names.size) {
            if(symbol !is ScopedSymbol) error("Invalid receiver: $symbol")
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
        is BinaryNode -> { resolveNode(node.left); resolveNode(node.right) }
        is OpNode     -> resolveNode(node.node)
        is NameNode   -> resolveNameNode(node)

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
        node.symbol = symbol
        return symbol
    }


}