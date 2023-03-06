package eyre

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var tokens: List<Token>

	private var pos = 0

	private var scopeStack = IntArray(64)

	private var scopeStackSize = 0

	private var currentScope = ScopeInterner.EMPTY

	private val nodes = ArrayList<AstNode>()

	private var currentNamespace: Namespace? = null // Only single-line namespaces



	/*
	Parsing utils
	 */



	private val next get() = tokens[pos]

	private val prev get() = tokens[pos - 1]

	private fun next() = tokens[pos++]

	private fun atNewline() = srcFile.newlines[pos]

	private fun atTerminator() = srcFile.terminators[pos]

	private fun id() = (tokens[pos++] as? IdToken)?.value ?: error("Expecting identifier, found: $prev")

	private fun SrcPos(offset: Int = 0) = SrcPos(srcFile, srcFile.tokenLines[pos - offset])



	/*
	Nodes and symbol creation
	 */



	private fun<T : AstNode> T.add(srcPos: SrcPos): T {
		this.srcPos = srcPos
		nodes.add(this)
		return this
	}

	private fun<T : AstNode> T.srcPos(srcPos: SrcPos): T {
		this.srcPos = srcPos;
		return this
	}

	private fun<T : Symbol> T.add(): T {
		context.symbols.add(this)?.let {
			error("Symbol redeclaration: $name. Original: ${it.scope}.${it.name}, new: $scope.$name")
		}
		return this
	}



	/*
	Errors
	 */



	private fun error(srcPos: SrcPos, message: String): Nothing {
		System.err.println("Parser error at ${srcFile.path}:${srcPos.line}")
		System.err.print('\t')
		System.err.println(message)
		System.err.println()
		throw RuntimeException("Parser error")
	}

	private fun error(offset: Int, message: String): Nothing = error(SrcPos(offset), message)

	private fun error(message: String): Nothing = error(SrcPos(), message)

	private fun expectTerminator() {
		if(!atTerminator())
			error("Expecting terminator")
	}

	private fun expect(symbol: SymToken) {
		if(next() != symbol)
			error("Expecting '${symbol.string}', found: $prev")
	}



	/*
	Node parsing
	 */



	private fun parseNamespace() {
		val srcPos = SrcPos()
		val name = id()
		val thisScope = addScope(name)
		val namespace = Namespace(SymBase(srcPos, currentScope, name), thisScope).add()
		val node = NamespaceNode(namespace).srcPos(srcPos)
	}
	private fun parseLabel(id: StringIntern) {
		val srcPos = SrcPos(1)
		pos++
		val symbol = LabelSymbol(SymBase(srcPos, currentScope, id)).add()
		LabelNode(symbol).add(srcPos)
	}



	private fun parseId(id: StringIntern) {
		if(next == SymToken.COLON) {
			parseLabel(id)
			return
		}

		if(id in StringInterner.keywords) {
			when(StringInterner.keywords[id]) {
				//Keyword.NAMESPACE -> parseNamespace()
				//Keyword.DLLIMPORT -> parseDllImport()
				//Keyword.VAR       -> parseVar()
				//Keyword.CONST     -> parseConst()
				//Keyword.ENUM      -> parseEnum(false)
				//Keyword.BITMASK   -> parseEnum(true)
				else              -> error("Invalid keyword: $id")
			}
		}

		if(id in StringInterner.mnemonics) {
			val srcPos = SrcPos()
			val mnemonic = StringInterner.mnemonics[id]
			//parseInstruction(mnemonic).add().srcPos = srcPos
		}
	}



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.tokens = srcFile.tokens
		scopeStackSize = 0
		nodes.clear()

		parseScope()

		if(currentNamespace != null)
			ScopeEndNode().add(SrcPos())

		srcFile.nodes = ArrayList(nodes)
	}



	private fun parseScope() {
		while(pos < tokens.size) {
			when(val token = next()) {
				is IdToken           -> parseId(token.value)
				SymToken.RBRACE -> { pos--; break }
				is SymToken          -> if(token != SymToken.SEMICOLON) error(1, "Invalid symbol: ${token.string}")
				EndToken             -> break
				else                 -> error(1, "Invalid token: $token")
			}
		}
	}



	private fun parseScope(scope: ScopeIntern) {
		val prevScope = currentScope
		currentScope = scope
		scopeStackSize++
		parseScope()
		scopeStackSize--
		currentScope = prevScope
	}



	private fun addScope(name: StringIntern): ScopeIntern {
		if(scopeStackSize >= scopeStack.size)
			scopeStack = scopeStack.copyOf(scopeStackSize * 2)
		scopeStack[scopeStackSize] = name.id
		val hash = currentScope.hash * 31 + name.id
		return ScopeInterner.add(scopeStack.copyOf(scopeStackSize + 1), hash)
	}


}