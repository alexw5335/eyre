package eyre

class Parser(private val context: Context) {


	private lateinit var srcFile: SrcFile
	private val tokens get() = srcFile.tokens

	private var pos = 0
	private var currentScope: Symbol = RootSym



	/*
	Util
	 */



	private fun Token.asName() = if(type != TokenType.NAME)
		err(srcPos(), "Expecting name, found: $type")
	else
		nameValue

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(message, srcPos)

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

	private fun<T : Node> T.add(): T {
		srcFile.nodes.add(this)
		return this
	}

	private fun<T> T.addSym(): T where T : Node, T : Symbol {
		srcFile.nodes.add(this)
		context.symTable.add(this)
		return this
	}

	private fun Token.srcPos() = SrcPos(srcFile, line)



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.pos = 0

		try {
			parseScope(RootSym)
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
				TokenType.NAME   -> parseKeyword(token)
				TokenType.RBRACE -> return
				TokenType.SEMI   -> pos++
				TokenType.EOF    -> break
				else             -> err("Invalid token: ${token.type}")
			}
		}

		currentScope = prevScope
	}



	private fun parseKeyword(keywordToken: Token) {
		val keyword = keywordToken.nameValue
		val srcPos = keywordToken.srcPos()

		if(tokens[pos + 1].type == TokenType.COLON) {
			pos++
			LabelNode(srcPos, currentScope, keyword).addSym()
			expectNewline()
			return
		}

		pos++

		when(keyword) {
			in Names.mnemonics -> {
				parseIns(srcPos, Names.mnemonics[keyword]!!)
			}

			Names.PROC -> {
				val proc = ProcNode(srcPos, currentScope, name()).addSym()
				expect(TokenType.LBRACE)
				parseScope(proc.scope)
				expect(TokenType.RBRACE)
				ScopeEndNode(proc).add()
			}

			Names.NAMESPACE -> {
				val namespace = NamespaceNode(srcPos, currentScope, name()).add()
				expectNewline()
				parseScope(namespace)
				ScopeEndNode(namespace).add()
			}

			Names.CONST -> {
				val name = name()
				expect(TokenType.SET)
				val valueNode = parseExpr()
				ConstNode(srcPos, currentScope, name, valueNode).addSym()
				expectNewline()
			}

			Names.TYPEDEF -> {
				val name = name()
				expect(TokenType.SET)
				val valueNode = parseExpr()
				TypedefNode(srcPos, currentScope, name, valueNode).addSym()
				expectNewline()
			}

			Names.STRUCT -> {
				val struct = StructNode(srcPos, currentScope, name()).add()
				expect(TokenType.LBRACE)

				while(tokens[pos].type != TokenType.RBRACE) {
					val memberSrcPos = tokens[pos].srcPos()
					val typeNode = parseExpr(requireTerminator = false)
					val memberName = name()
					val member = MemberNode(memberSrcPos, struct, memberName, typeNode)
					context.symTable.add(member)
					struct.members.add(member)
					expectNewline()
				}
			}

			Names.ENUM -> {
				val enum = EnumNode(srcPos, currentScope, name()).add()
				expect(TokenType.LBRACE)

				while(true) {
					if(tokens[pos].type == TokenType.RBRACE)
						break
					val nameToken = tokens[pos++]
					val entryName = nameToken.asName()

					val valueNode = if(tokens[pos].type == TokenType.SET) {
						pos++
						parseExpr()
					} else
						null

					val entry = EnumEntryNode(nameToken.srcPos(), enum, entryName, valueNode)
					context.symTable.add(entry)
					enum.entries.add(entry)

					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}

			}

			else -> err("Invalid token: $keyword")
		}
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]
		val srcPos = token.srcPos()

		return when(token.type) {
			TokenType.NAME   -> if(token.nameValue in Names.regs)
				RegNode(srcPos, Names.regs[token.nameValue]!!)
			else
				NameNode(srcPos, token.nameValue)
			TokenType.REG    -> RegNode(srcPos, token.regValue)
			TokenType.INT    -> IntNode(srcPos, token.value)
			TokenType.STRING -> StringNode(srcPos, token.stringValue(context))
			TokenType.CHAR   -> IntNode(srcPos, token.value)
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			else             -> UnNode(srcPos, token.type.unOp ?: err(srcPos, "Invalid atom: $token"), parseAtom())
		}
	}



	private fun parseExpr(precedence: Int = 0, requireTerminator: Boolean = true): Node {
		var left = parseAtom()

		while(true) {
			val token = tokens[pos]

			if(!token.isSym) {
				pos++
				if(atNewline || !requireTerminator) {
					pos--
					break
				} else
					err(left.srcPos, "Invalid binary operator token: $token")
			}

			val op = token.type.binOp ?: break
			if(op.precedence < precedence) break
			pos++
			val right = parseExpr(op.precedence + 1)
			left = when(op) {
				BinOp.ARR -> ArrayNode(left.srcPos, left, right).also { expect(TokenType.RBRACK) }
				BinOp.DOT -> DotNode(left.srcPos, left, right)
				else      -> BinNode(left.srcPos, op, left, right)
			}
		}

		return left
	}



	private fun parseOperand(): OpNode {
		var token = tokens[pos]
		var width = Width.NONE
		val srcPos = token.srcPos()

		if(token.type == TokenType.NAME && token.nameValue in Names.widths) {
			width = Names.widths[token.nameValue]!!
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

		return OpNode(srcPos, type, width, reg, child)
	}



	private fun parseIns(srcPos: SrcPos, mnemonic: Mnemonic) {
		var op1: OpNode? = null
		var op2: OpNode? = null
		var op3: OpNode? = null
		var op4: OpNode? = null

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

		InsNode(srcPos, mnemonic, op1, op2, op3, op4).add()
		expectNewline()
	}


}