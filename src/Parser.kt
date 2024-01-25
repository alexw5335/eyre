package eyre

class Parser(private val context: Context) {


	private lateinit var srcFile: SrcFile
	private val tokens get() = srcFile.tokens

	private var pos = 0
	private var currentScope: Symbol = RootSym



	/*
	Util
	 */



	private fun skipComma() {
		if(tokens[pos].type == TokenType.COMMA) pos++
	}

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



	fun parse() {
		for(s in context.files)
			if(!s.invalid)
				parse(s)
	}



	private fun parse(srcFile: SrcFile) {
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
			Names.STRUCT -> parseStruct(srcPos, name(), false, null)
			Names.UNION  -> parseStruct(srcPos, name(), true, null)

			Names.FUN -> {
				val funNode = FunNode(srcPos, currentScope, name()).addSym()
				expect(TokenType.LPAREN)
				while(tokens[pos].type != TokenType.RPAREN) {
					val paramSrcPos = tokens[pos].srcPos()
					val paramName = name()
					expect(TokenType.COLON)
					val paramType = parseType()
					skipComma()
					val param = ParamNode(paramSrcPos, funNode, paramName, paramType)
					context.symTable.add(param)
					funNode.params.add(param)
				}
				pos++
				expect(TokenType.LBRACE)
				parseScope(funNode)
				expect(TokenType.RBRACE)
				ScopeEndNode(funNode).add()
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

			Names.VAR -> {
				val name = name()
				expect(TokenType.COLON)
				val typeNode = parseType()
				val valueNode: Node? = if(tokens[pos].type == TokenType.SET) {
					pos++
					parseExpr()
				} else
					null
				val varNode = VarNode(srcPos, currentScope, name, typeNode, valueNode).addSym()
				if(currentScope is FunNode) {
					varNode.mem.type = Mem.Type.STACK
					(currentScope as FunNode).locals.add(varNode)
				} else {
					varNode.mem.type = Mem.Type.GLOBAL
				}
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

			else -> {
				pos--
				parseExpr().add()
			}
		}
	}



	private fun parseStruct(srcPos: SrcPos, name: Name, isUnion: Boolean, parent: StructNode?): StructNode {
		val struct = StructNode(srcPos, parent ?: currentScope, name, isUnion)
		val scope: Symbol

		if(parent != null) {
			val member = MemberNode(struct.srcPos, parent, struct.name, null, struct)
			parent.members.add(member)
			scope = parent
		} else {
			if(name.isNull)
				err(srcPos, "Top-level struct cannot be anonymous")
			struct.addSym()
			scope = struct
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
			val member = MemberNode(first.srcPos(), scope, memberName, typeNode, null)
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

			TokenType.NAME   -> NameNode(srcPos, token.nameValue)
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