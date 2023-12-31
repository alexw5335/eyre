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



	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(message, srcPos)

	private fun err(message: String): Nothing = err(srcPos(), message)

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
		if(tokens[pos + 1].type == TokenType.COLON) {
			parseLabel()
			return
		}

		when(name) {
			in Name.mnemonics -> parseIns(Name.mnemonics[name]!!)
		}
	}



	private fun parseLabel() {
		val srcPos = srcPos()
		val name = name()
		pos += 2
		val node = LabelNode(name).add()
		node.srcPos = srcPos
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]
		val srcPos = srcPos()

		return when(token.type) {
			TokenType.NAME   -> if(token.nameValue in Name.regs)
				RegNode(Name.regs[token.nameValue]!!)
			else
				NameNode(token.nameValue)
			TokenType.INT    -> IntNode(token.value)
			TokenType.STRING -> StringNode(token.stringValue(context))
			TokenType.CHAR   -> IntNode(token.value)
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			else             -> UnNode(token.type.unOp ?: err(srcPos, "Invalid atom: $token"), parseAtom())
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
			left.srcPos = left.left.srcPos
		}

		return left
	}



	private fun parseOperand(): OpNode {
		var token = tokens[pos]
		var width = Width.NONE

		if(token.type == TokenType.NAME && token.nameValue in Name.widths) {
			width = Name.widths[token.nameValue]!!
			token = tokens[++pos]
		}

		if(token.type == TokenType.LBRACK) {
			pos++
			val value = parseExpr()
			expect(TokenType.RBRACK)
			return OpNode.mem(width, value)
		}

		if(tokens[pos].type == TokenType.REG) {
			if(width != Width.NONE)
				err("Width specifier not allowed for register operands")
			return OpNode.reg(tokens[pos++].regValue)
		}

		return OpNode.imm(width, parseExpr())
	}



	private fun parseIns(mnemonic: Mnemonic) {
		val srcPos = srcPos()
		pos++

		var op1 = OpNode.NONE
		var op2 = OpNode.NONE
		var op3 = OpNode.NONE
		var op4 = OpNode.NONE

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
					if(commaOrNewline()) {
						op4 = parseOperand()
						expectNewline()
					}
				}
			}
		}

		val node = InsNode(mnemonic, op1, op2, op3, op4).add()
		node.srcPos = srcPos
		node.add()
	}


}