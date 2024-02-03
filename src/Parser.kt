package eyre

class Parser(private val context: Context) {


	private lateinit var file: SrcFile
	private lateinit var tokens: List<Token>
	private var pos = 0



	/*
	Util
	 */



	private fun skipComma() {
		if(tokens[pos].type == TokenType.COMMA) pos++
	}

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(srcPos, message)

	private fun err(message: String): Nothing = err(tokens[pos].srcPos(), message)

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

	private fun expectNewline() {
		if(!atNewline)
			err("Expecting newline")
	}

	private val atNewline get() = tokens[pos].line != tokens[pos - 1].line

	private fun<T : Sym> T.add(): T {
		context.symTable.add(this)
		return this
	}

	private fun Token.srcPos() = SrcPos(file, line)


	/*
	Parsing
	 */



	fun parse() {
		for(s in context.files)
			if(!s.invalid)
				parse(s)
	}



	private fun parse(file: SrcFile) {
		this.file = file
		this.tokens = file.tokens
		this.pos = 0

		try {
			parseScope(null, file.nodes)
		} catch(_: EyreError) {
			file.invalid = true
		}
	}



	private fun parseScope(scope: Sym?, nodes: ArrayList<Node>) {
		while(pos < tokens.size) {
			val token = tokens[pos]
			when(token.type) {
				TokenType.NAME   -> parseKeyword(token, scope, nodes)
				TokenType.RBRACE -> return
				TokenType.SEMI   -> pos++
				TokenType.EOF    -> break
				else             -> err("Invalid token: ${token.type}")
			}
		}
	}



	private fun parseKeyword(keywordToken: Token, scope: Sym?, nodes: ArrayList<Node>) {
		val keyword = keywordToken.nameValue
		val srcPos = keywordToken.srcPos()

		fun<T : Node> T.add(): T {
			nodes.add(this)
			return this
		}

		if(tokens[pos + 1].type == TokenType.COLON) {
			pos++
			LabelNode(srcPos, LabelSym(SymBase(scope, keyword)).add()).add()
			expectNewline()
			return
		}

		pos++

		when(keyword) {
			in Name.mnemonics -> parseIns(srcPos, Name.mnemonics[keyword]!!).add()

			Name.PROC -> {
				val name = name()
				expect(TokenType.LBRACE)
				val sym = ProcSym(SymBase(scope, name)).add()
				val children = ArrayList<Node>()
				parseScope(sym, children)
				expect(TokenType.RBRACE)
				ProcNode(srcPos, sym, children).add()
			}

			Name.TYPEDEF -> {
				val name = name()
				expect(TokenType.SET)
				val typeNode = parseType()
				TypedefNode(srcPos, TypedefSym(SymBase(scope, name)).add(), typeNode).add()
				expectNewline()
			}

			else -> {
				pos--
				parseExpr().add()
			}
		}
	}



	/*
	Expression parsing
	 */



	private fun parseOperand(): OpNode {
		val token = tokens[pos]
		val srcPos = token.srcPos()

		return if(token.type == TokenType.NAME && token.nameValue in Name.widths) {
			pos++
			expect(TokenType.LBRACK)
			val child = parseExpr()
			expect(TokenType.RBRACK)
			OpNode(srcPos, OpType.MEM, Name.widths[token.nameValue]!!, child, Reg.NONE)
		} else if(token.type == TokenType.LBRACK) {
			pos++
			val child = parseExpr()
			expect(TokenType.RBRACK)
			OpNode(srcPos, OpType.MEM, Width.NONE, child, Reg.NONE)
		} else if(token.type == TokenType.REG) {
			pos++
			val reg = token.regValue
			OpNode(srcPos, reg.type, reg.width, null, reg)
		} else {
			OpNode(srcPos, OpType.IMM, Width.NONE, parseExpr(), Reg.NONE)
		}
	}



	private fun parseIns(srcPos: SrcPos, mnemonic: Mnemonic): InsNode {
		var op1: OpNode? = null
		var op2: OpNode? = null
		var op3: OpNode? = null

		fun commaOrNewline(): Boolean {
			if(atNewline) return false
			if(tokens[pos++].type != TokenType.COMMA)
				err(srcPos, "Expecting newline or comma")
			return true
		}

		if(!atNewline) {
			op1 = parseOperand()
			if(commaOrNewline()) {
				op2 = parseOperand()
				if(commaOrNewline()) {
					op3 = parseOperand()
					expectNewline()
				}
			}
		}

		expectNewline()
		return InsNode(srcPos, mnemonic, op1, op2, op3)
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]
		val srcPos = token.srcPos()

		return when(token.type) {
			TokenType.LBRACE -> {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RBRACE) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				InitNode(srcPos, elements)
			}

			TokenType.REG    -> RegNode(srcPos, token.regValue)
			TokenType.NAME   -> NameNode(srcPos, token.nameValue)
			TokenType.INT    -> IntNode(srcPos, token.intValue)
			TokenType.STRING -> StringNode(srcPos, token.stringValue)
			TokenType.CHAR   -> IntNode(srcPos, token.intValue)
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			else             -> UnNode(srcPos, token.type.unOp ?: err(srcPos, "Invalid atom: $token"), parseAtom())
		}
	}



	private fun parseType(): TypeNode {
		val srcPos = tokens[pos].srcPos()
		val names = ArrayList<Name>()

		while(true) {
			names.add(name())
			if(tokens[pos].type != TokenType.DOT) break
			pos++
		}

		if(tokens[pos].type != TokenType.LBRACK)
			return TypeNode(srcPos, names, emptyList())

		val arraySizes = ArrayList<Node>()

		do {
			pos++
			arraySizes.add(parseExpr())
			expect(TokenType.RBRACK)
		} while(tokens[pos].type == TokenType.LBRACK)

		return TypeNode(srcPos, names, arraySizes)
	}



	private fun parseExpr(precedence: Int = 0, requireTerminator: Boolean = true): Node {
		var left = parseAtom()

		while(true) {
			val token = tokens[pos]
			if(!token.isSym)
				if(atNewline || !requireTerminator)
					break
				else
					err(left.srcPos, "Invalid binary operator token: $token")
			val op = token.type.binOp ?: break
			if(op.precedence < precedence) break
			pos++
			if(op == BinOp.INV) {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RPAREN) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				left = CallNode(left.srcPos, left, elements)
			} else {
				val right = parseExpr(op.precedence + 1)
				left = when(op) {
					BinOp.ARR -> ArrayNode(left.srcPos, left, right).also { expect(TokenType.RBRACK) }
					BinOp.DOT -> DotNode(left.srcPos, left, right)
					BinOp.REF -> RefNode(left.srcPos, left, right)
					else      -> BinNode(left.srcPos, op, left, right)
				}
			}
		}

		return left
	}


}