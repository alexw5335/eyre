package eyre

class Parser(private val context: Context) {


	private lateinit var srcFile: SrcFile
	private val nodes get() = srcFile.nodes
	private val tokens get() = srcFile.tokens

	private var lineCount = 0
	private var pos = 0



	/*
	Util
	 */



	private fun err(message: String, srcPos: SrcPos? = srcPos()): Nothing =
		context.err(message, srcPos)

	private fun name(): Name {
		val token = tokens[pos++]
		if(token.type != TokenType.NAME)
			err("Expecting name, found: ${token.type}")
		return token.nameValue
	}

	private fun expect(type: TokenType) {
		if(tokens[pos++].type != type)
			err("Expecting $type, found: ${tokens[pos-1].type}")
	}

	private val atNewline get() = tokens[pos].type == TokenType.NEWLINE

	private fun<T : Node> T.add(): T {
		srcFile.nodes.add(this)
		return this
	}

	private fun srcPos() = SrcPos(srcFile, lineCount)



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.lineCount = 1
		this.pos = 0

		try {
			parseScope()
		} catch(_: EyreError) {
			srcFile.invalid = true
		}
	}



	private fun parseScope() {
		while(pos < tokens.size) {
			val token = tokens[pos]
			when(token.type) {
				TokenType.NAME    -> parseName(token.nameValue)
				TokenType.NEWLINE -> { pos++; lineCount++; }
				TokenType.RBRACE  -> return
				TokenType.SEMI    -> pos++
				else              -> err("Invalid token: ${token.type}")
			}
		}
	}



	private fun parseName(name: Name) {
		parseExpr().add()
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]
		val srcPos = srcPos()

		return when(token.type) {
			TokenType.NAME   -> NameNode(token.nameValue)
			TokenType.INT    -> IntNode(token.value)
			TokenType.STRING -> StringNode(token.stringValue(context))
			TokenType.CHAR   -> IntNode(token.value)
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			else             -> UnNode(token.type.unOp ?: err("Invalid atom: $token", srcPos), parseAtom())
		}.also { it.srcPos = srcPos }
	}



	private fun parseExpr(precedence: Int = 0): Node {
		var left = parseAtom()

		while(true) {
			val token = tokens[pos]
			val op = token.type.binOp ?: break
			if(op.precedence < precedence) break
			pos++
			val expression = parseExpr(op.precedence + 1)
			left = BinNode(op, left, expression)
		}

		return left
	}


}