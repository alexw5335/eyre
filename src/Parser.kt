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

	private fun potentialName() = if(tokens[pos].type == TokenType.NAME)
		tokens[pos++].nameValue
	else
		Names.NONE

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
			in Names.mnemonics -> parseIns(srcPos, Names.mnemonics[keyword]!!)
			Names.STRUCT       -> parseStruct(srcPos, name(), false, null)
			Names.UNION        -> parseStruct(srcPos, name(), true, null)

			Names.PROC -> {
				val proc = ProcNode(srcPos, currentScope, name()).addSym()
				expect(TokenType.LBRACE)
				parseScope(proc.scope)
				expect(TokenType.RBRACE)
				ScopeEndNode(proc).add()
			}

			Names.NAMESPACE -> {
				var namespace = NamespaceNode(srcPos, currentScope, name())
				context.symTable.add(namespace)
				while(tokens[pos].type == TokenType.DOT) {
					pos++
					namespace = NamespaceNode(srcPos, namespace, name())
					context.symTable.add(namespace)
				}
				namespace.add()
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
				val typeNode = parseType()
				TypedefNode(srcPos, currentScope, name, typeNode).addSym()
				expectNewline()
			}

			Names.ENUM -> {
				val enum = EnumNode(srcPos, currentScope, name()).addSym()
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
				pos++
			}

			else -> err("Invalid token: $keyword")
		}
	}



	private fun parseStruct(srcPos: SrcPos, name: Name, isUnion: Boolean, parent: StructNode?): StructNode {
		val isAnon = name.isNull
		if(isAnon && parent == null)
			err(srcPos, "Anonymous struct not allowed here")

		val struct = StructNode(srcPos, parent ?: currentScope, name, isUnion)

		if(parent == null)
			struct.addSym()
		else {
			if(!isAnon)
				context.symTable.add(struct)
			parent.members.add(struct)
		}

		expect(TokenType.LBRACE)

		while(tokens[pos].type != TokenType.RBRACE) {
			val first = tokens[pos]

			if(first.type == TokenType.NAME) {
				when(val n = first.nameValue) {
					Names.UNION, Names.STRUCT -> {
						pos++
						parseStruct(first.srcPos(), potentialName(), n == Names.UNION, struct)
						continue
					}
				}
			}

			val typeNode = parseType()
			val memberName = name()
			val scope = if(isAnon) parent!! else struct
			val member = MemberNode(first.srcPos(), scope, memberName, typeNode)
			context.symTable.add(member)
			struct.members.add(member)
			expectNewline()
		}

		pos++

		return struct
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]
		val srcPos = token.srcPos()

		return when(token.type) {
			TokenType.NAME -> when(token.nameValue) {
				in Names.regs -> RegNode(srcPos, Names.regs[token.nameValue]!!)
				else          -> NameNode(srcPos, token.nameValue)
			}
			TokenType.REG    -> RegNode(srcPos, token.regValue)
			TokenType.INT    -> IntNode(srcPos, token.value)
			TokenType.STRING -> StringNode(srcPos, token.stringValue(context))
			TokenType.CHAR   -> IntNode(srcPos, token.value)
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
			val right = parseExpr(op.precedence + 1)
			left = when(op) {
				BinOp.ARR -> ArrayNode(left.srcPos, left, right).also { expect(TokenType.RBRACK) }
				BinOp.DOT -> DotNode(left.srcPos, left, right)
				BinOp.REF -> RefNode(left.srcPos, left, right)
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