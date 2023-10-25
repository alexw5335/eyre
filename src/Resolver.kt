package eyre

class Resolver(private val context: Context) {


	private var scopeStack = Array(64) { Scope.NULL }

	private var scopeStackSize = 0

	private var importStack = Array(64) { ArrayList<Sym>() }

	private var scopeStackStart = 0



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

	private fun err(srcPos: SrcPos, message: String): Nothing = context.err(srcPos, message)

	private fun err(message: String): Nothing = context.err(SrcPos(), message)



	fun resolve() {
		for(srcFile in context.srcFiles)
			resolveNodesInFile(srcFile)
	}



	private fun visit(file: SrcFile, block: (Node) -> Unit) {
		val prevScopeStackStart = scopeStackStart
		scopeStackStart = scopeStackSize

		for(node in file.nodes) {
			try {
				block(node)
			} catch(e: EyreError) {
				if(e.srcPos.isNull)
					e.srcPos = node.srcPos
				file.invalid = true
				break
			}
		}

		scopeStackStart = prevScopeStackStart
	}



	private fun resolveNodesInFile(file: SrcFile) {
		if(file.resolved)
			return
		if(file.resolving)
			err(SrcPos(), "Cyclic compile-time file dependency")
		file.resolving = true
		visit(file, ::resolveNode)
		file.resolved = true
		file.resolving = false
	}



	/*
	Name resolution
	 */



	/**
	 * Resolves a [name] within the current scope context.
	 */
	private fun resolveName(name: Name): Sym {
		for(i in scopeStackSize - 1 downTo scopeStackStart) {
			val scope = scopeStack[i]
			context.symbols.get(scope, name)?.let { return it }
		}

		context.symbols.get(Scope.NULL, name)?.let { return it }

		for(i in scopeStackSize - 1 downTo scopeStackStart) {
			for(import in importStack[i]) {
				if(import is ScopedSym) {
					context.symbols.get(import.thisScope, name)?.let { return it }
				}
			}
		}

		err("Unresolved symbol: $name")
	}



	/**
	 * Resolves a series of [names] within the current scope context.
	 */
	private fun resolveNames(names: Array<Name>): Sym {
		var symbol = resolveName(names[0])

		for(i in 1 ..< names.size) {
			if(symbol !is ScopedSym)
				err("Invalid receiver: ${symbol.place.name}")

			symbol = context.symbols.get(symbol.thisScope, names[i]) ?: error("Unresolved symbol: ${names[i]}")
		}

		return symbol
	}



	/*
	Stage 2: Symbol resolution and constant calculation - full visit
	 */



	private fun resolveNodeFile(node: TopNode) {
		if(node.srcPos.isNull)
			context.internalErr("Missing source position: $node")
		resolveNodesInFile(context.srcFiles[node.srcPos.file])
	}



	private fun resolveInt(node: Node): Long {
		fun sym(sym: Sym): Long {
			if(!sym.resolved)
				if(sym is TopNode)
					resolveNodeFile(sym)
				else
					context.internalErr()

			if(sym is Const)
				return sym.intValue

			err("Invalid int constant node")
		}

		return when(node) {
			is IntNode  -> node.value
			is UnNode   -> node.calc(::resolveInt)
			is NameNode -> sym(resolveNameNode(node))
			is BinNode  -> node.calc(::resolveInt)
			else        -> err("Invalid integer constant node: $node")
		}
	}



	private fun resolveNode(node: Node) { when(node) {
		is Namespace  -> pushScope(node.thisScope)
		is Proc       -> pushScope(node.thisScope)
		is ScopeEnd   -> popScope()

		is InsNode -> {
			if(node.mnemonic.isPseudo) return
			if(node.op1.isNotNone) resolveNode(node.op1)
			if(node.op2.isNotNone) resolveNode(node.op2)
			if(node.op3.isNotNone) resolveNode(node.op3)
			if(node.op4.isNotNone) resolveNode(node.op4)
		}
		
		is OpNode   -> if(node.node != NullNode) resolveNode(node.node)
		is NameNode -> resolveNameNode(node)
		is Const    -> { } //node.resolve { intValue = resolveInt(valueNode) }
		is UnNode   -> resolveNode(node.node)
		is BinNode  -> { resolveNode(node.left); resolveNode(node.right) }

		is RegNode,
		is StringNode,
		is IntNode,
		is Label -> Unit

		NullNode -> context.internalErr("Invalid node: $node")
	}}



	private fun resolveNameNode(node: NameNode): Sym {
		val symbol = resolveName(node.value)
		node.sym = symbol
		return symbol
	}


}