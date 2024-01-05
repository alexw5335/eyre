package eyre

class Parser(private val context: Context) {


	private lateinit var srcFile: SrcFile
	private val nodes get() = srcFile.nodes
	private val tokens get() = srcFile.tokens

	private var lineCount = 0
	private var pos = 0
	private var currentScope: Symbol = context.symTable.root



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

	private fun place(name: Name) = Place(currentScope.place.id, name.id)



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.lineCount = 1
		this.pos = 0

		try {
			parseScope(Place.NULL)
		} catch(_: EyreError) {
			srcFile.invalid = true
		}
	}



	private fun parseScope(scope: Symbol) {
		val prevScope = currentScope
		currentScope = scope

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

		currentScope = prevScope
	}



	private fun parseName(name: Name) {
		if(tokens[pos + 1].type == TokenType.COLON) {
			parseLabel()
			return
		}

		when(name) {
			in Name.mnemonics -> parseIns()
			Name.PROC -> parseProc()
		}
	}



	private fun parseLabel() {
		val srcPos = srcPos()
		val name = name()
		pos += 2
		val node = LabelNode(place(name)).add()
		node.srcPos = srcPos
	}



	private fun parseProc() {
		val srcPos = srcPos()
		pos++
		val name = name()
		val proc = ProcNode(place(name)).add()
		proc.srcPos = srcPos
		expect(TokenType.LBRACE)
		parseScope(proc.scope)
		expect(TokenType.RBRACE)
		ScopeEndNode(proc).add()
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]
		val srcPos = srcPos()

		return when(token.type) {
			TokenType.NAME   -> if(token.nameValue in Name.regs)
				RegNode(Name.regs[token.nameValue]!!)
			else
				NameNode(token.nameValue)
			TokenType.REG    -> RegNode(token.regValue)
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
		val srcPos = srcPos()
		var token = tokens[pos]
		var width = Width.NONE

		if(token.type == TokenType.NAME && token.nameValue in Name.widths) {
			width = Name.widths[token.nameValue]!!
			token = tokens[++pos]
		}

		val child: Node
		val reg: Reg
		val type: OpType

		if(token.type == TokenType.LBRACK) {
			pos++
			child = parseExpr()
			expect(TokenType.RBRACK)
			type = OpType.MEM
			reg = Reg.NONE
		} else if(token.type == TokenType.REG) {
			if(width != Width.NONE)
				err("Width specifier not allowed for register operands")
			reg = token.regValue
			type = reg.type
			child = NullNode
			pos++
		} else {
			type = OpType.IMM
			child = parseExpr()
			reg = Reg.NONE
		}

		val node = OpNode(type, width, reg, child)
		node.srcPos = srcPos
		return node
	}



	private fun parseIns() {
		val srcPos = srcPos()
		val mnemonic = Name.mnemonics[tokens[pos++].nameValue]!!

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
						expect(TokenType.NEWLINE)
					}
				}
			}
		}

		val node = InsNode(mnemonic, op1, op2, op3, op4).add()
		node.srcPos = srcPos
	}


}