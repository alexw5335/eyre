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
			if(node.mnemonic.isPseudo) return
			resolveNode(node.op1 ?: return)
			resolveNode(node.op2 ?: return)
			resolveNode(node.op3 ?: return)
			resolveNode(node.op4 ?: return)
		}
		is VarResNode -> node.symbol.type = resolveTypeNode(node.type)

		else -> return
	}}



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




}