package eyre

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<AstNode>

	private lateinit var tokens: List<Token>

	private var pos = 0

	private var finished = false

	private val atNewline get() = srcFile.newlines[pos]

	private val atTerminator get() = srcFile.terminators[pos]

	private fun srcPos() = SrcPos(srcFile, srcFile.tokenLines[pos])

	private var currentScope = Scopes.EMPTY

	private fun SymBase(name: Name) = SymBase(currentScope, name)



	private fun parserError(srcPos: SrcPos, message: String) {
		context.errors.add(EyreError(srcPos, message))
		finished = true
		srcFile.invalid = true
	}



	private fun parserError(offset: Int, message: String) {
		parserError(SrcPos(srcFile, srcFile.tokenLines[pos - offset]), message)
	}




	private fun<T : AstNode> T.add(srcPos: SrcPos): T{
		nodes.add(this)
		this.srcPos = srcPos
		return this
	}



	private fun<T : Symbol> T.add(): T {
		val existing = context.symbols.add(this)
		if(existing != null)
			parserError(node?.srcPos ?: context.internalError(), "Symbol redeclaration: ${existing.node?.srcPos}")
		return this
	}



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.nodes = srcFile.nodes
		this.tokens = srcFile.tokens
		pos = 0

		while(!finished) {
			when(val token = tokens[pos]) {
				is Name            -> parseName(token)
				SymToken.RBRACE    -> break
				SymToken.HASH      -> { }
				EndToken           -> finished = true
				SymToken.SEMICOLON -> pos++
				is SymToken        -> parserError(1, "Invalid symbol: ${token.string}")
				else               -> parserError(1, "Invalid token: $token")
			}
		}
	}


	/**
	 * Pos is before the [name] token.
	 */
	private fun parseName(name: Name) {
		if(tokens[pos+1] == SymToken.COLON) {
			val srcPos = srcPos()
			pos += 2
			val symbol = LabelSymbol(SymBase(name))
			LabelNode(symbol).add(srcPos)
			symbol.add()
			return
		}

		if(name in Names.keywords) {
			when(Names.keywords[name]) {
				//Keyword.NAMESPACE -> parseNamespace()
				//Keyword.VAR       -> parseVar(false)
				//Keyword.VAL       -> parseVar(true)
				//Keyword.CONST     -> parseConst()
				//Keyword.ENUM      -> parseEnum(false)
				//Keyword.BITMASK   -> parseEnum(true)
				//Keyword.TYPEDEF   -> parseTypedef()
				//Keyword.PROC      -> parseProc()
				//Keyword.IMPORT    -> parseImport()
				//Keyword.STRUCT    -> parseStruct()
				else -> context.internalError()
			}
			return
		}

		/*
				if(id in Names.prefixes) {
			val next = id()
			if(next !in Names.mnemonics) error("Invalid prefix: $next")
			parseInstruction(Names.prefixes[id], Names.mnemonics[next]).add()
			return
		}

		if(id in Names.mnemonics) {
			parseInstruction(null, Names.mnemonics[id]).add()
			return
		}
		 */

		parserError(0, "Invalid identifier: $name")
	}



}