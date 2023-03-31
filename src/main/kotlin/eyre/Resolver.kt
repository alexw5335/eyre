package eyre

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
		context.srcFiles.forEach(::resolveFile)
	}



	private fun resolveFile(srcFile: SrcFile) {

	}



	fun AstNode.getSym(): Symbol? = when(this) {
		is NameNode   -> this.symbol
		is BinaryNode -> right.getSym()
		else          -> error("Cannot get symbol from node: $this")
	}


}