package eyre

class Parser(private val context: Context) {


	private lateinit var file: SrcFile
	private lateinit var tokens: List<Token>
	private lateinit var nodes: ArrayList<Node>
	private var pos = 0
	private var anonCount = 0
	private var scope: Sym? = null



	/*
	Util
	 */



	private fun<T : Node> T.addNode(): T {
		nodes.add(this)
		return this
	}

	private fun<T : Sym> T.addSym(): T {
		context.symTable.add(this)
		return this
	}

	private fun<T> T.addNodeSym(): T where T : Node, T : Sym {
		nodes.add(this)
		context.symTable.add(this)
		return this
	}

	private fun potentialName() = if(tokens[pos].type == TokenType.NAME)
		tokens[pos++].nameValue
	else
		Name.NONE

	private fun Token.asName() = if(type != TokenType.NAME)
		err(srcPos(), "Expecting name, found: $type")
	else
		nameValue

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

	private fun Token.srcPos() = SrcPos(file, line)

	private fun anon() = Name.anon(anonCount++)


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
		this.nodes = file.nodes
		this.pos = 0

		try {
			parseScope(null)
		} catch(_: EyreError) {
			file.invalid = true
		}
	}



	private fun parseBracedScope(scope: Sym) {
		expect(TokenType.LBRACE)
		parseScope(scope)
		expect(TokenType.RBRACE)
	}



	private fun parseScope(scope: Sym?) {
		val prevScope = this.scope
		this.scope = scope

		while(pos < tokens.size) {
			val token = tokens[pos]
			when(token.type) {
				TokenType.NAME   -> parseKeyword(token)
				TokenType.RBRACE -> break
				TokenType.SEMI   -> pos++
				TokenType.EOF    -> break
				else             -> err("Invalid token: ${token.type}")
			}
		}

		this.scope = prevScope

		if(scope != null)
			ScopeEndNode(Base(tokens[pos].srcPos()), scope).addNode()
	}



	private fun parseKeyword(keywordToken: Token) {
		val keyword = keywordToken.nameValue
		val srcPos = keywordToken.srcPos()

		fun<T : Node> T.addNode(): T {
			nodes.add(this)
			return this
		}

		fun<T : Sym> T.addSym(): T {
			context.symTable.add(this)
			return this
		}

		fun<T> T.addNodeSym(): T where T : Node, T : Sym {
			nodes.add(this)
			context.symTable.add(this)
			return this
		}

		if(tokens[pos + 1].type == TokenType.COLON) {
			pos += 2
			LabelNode(Base(srcPos, scope, keyword)).addNodeSym()
			expectNewline()
			return
		}

		pos++

		when(keyword) {
			in Name.mnemonics -> parseIns(srcPos, Name.mnemonics[keyword]!!).addNode()

			Name.IF -> {
				var parent = IfNode(Base(srcPos, scope, anon()), parseExpr(), null).addNode()
				parseBracedScope(parent)

				while(true) {
					if(tokens[pos].type != TokenType.NAME)
						break
					else if(tokens[pos].nameValue == Name.ELIF) {
						pos++
						val next = IfNode(Base(srcPos, scope, anon()), parseExpr(), parent).addNode()
						parseBracedScope(next)
						parent.next = next
						parent = next
					} else if(tokens[pos].nameValue == Name.ELSE) {
						pos++
						val next = IfNode(Base(srcPos, scope, anon()), null, parent).addNode()
						parent.next = next
						parseBracedScope(next)
						break
					} else
						break
				}
			}

			Name.STRUCT -> parseStruct(srcPos, scope, name(), false).addNodeSym()
			Name.UNION  -> parseStruct(srcPos, scope, name(), true).addNodeSym()

			Name.VAR -> {
				val name = name()

				val typeNode = if(tokens[pos].type == TokenType.COLON) {
					pos++
					parseType()
				} else null

				val valueNode = if(tokens[pos].type == TokenType.SET) {
					pos++
					parseExpr()
				} else null

				if(valueNode is InitNode && typeNode != null && typeNode.mods.size == 1) {
					val mod = typeNode.mods[0]
					if(mod is TypeNode.ArrayMod && mod.sizeNode == null)
						mod.inferredSize = valueNode.elements.size
				}

				VarNode(Base(srcPos, scope, name), typeNode, valueNode).addNodeSym()
			}

			Name.ENUM -> {
				val enum = EnumNode(Base(srcPos, scope, name())).addNodeSym()
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

					val entry = EnumEntryNode(Base(nameToken.srcPos(), enum, entryName), valueNode).addSym()
					enum.entries.add(entry)

					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
			}

			Name.DLLCALL -> {
				val first = name()
				if(tokens[pos].type == TokenType.DOT) {
					pos++
					DllCallNode(Base(srcPos), first, name()).addNode()
				} else
					DllCallNode(Base(srcPos), Name.NONE, first).addNode()
			}

			Name.NAMESPACE -> {
				var namespace = NamespaceNode(Base(srcPos, scope, name())).addSym()
				while(tokens[pos].type == TokenType.DOT) {
					pos++
					namespace = NamespaceNode(Base(srcPos, namespace, name())).addSym()
				}
				namespace.addNode()
				expectNewline()
				parseScope(namespace)
				ScopeEndNode(Base(tokens[pos].srcPos()), namespace).addNode()
			}

			Name.PROC -> {
				val name = name()
				val proc = ProcNode(Base(srcPos, scope, name)).addNodeSym()
				parseBracedScope(proc)
			}

			Name.CONST -> {
				val name = name()
				expect(TokenType.SET)
				val valueNode = parseExpr()
				ConstNode(Base(srcPos, scope, name), valueNode).addNodeSym()
				expectNewline()
			}


			Name.TYPEDEF -> {
				val name = name()
				expect(TokenType.SET)
				val typeNode = parseType()
				TypedefNode(Base(srcPos, scope, name), typeNode).addNodeSym()
				expectNewline()
			}
		}
	}




	private fun parseStruct(
		srcPos: SrcPos,
		parent: Sym?,
		name: Name,
		isUnion: Boolean,
	): StructNode {
		val struct = StructNode(Base(srcPos, parent, name), isUnion)
		val scope: Sym

		if(parent is StructNode) {
			val member = MemberNode(Base(struct.srcPos, parent, struct.name), null, struct)
			parent.members.add(member)
			scope = parent
		} else {
			if(name.isNull)
				err(srcPos, "Top-level struct cannot be anonymous")
			context.symTable.add(struct)
			scope = struct
		}

		expect(TokenType.LBRACE)

		while(tokens[pos].type != TokenType.RBRACE) {
			val first = tokens[pos]

			if(first.type == TokenType.NAME) {
				when(val n = first.nameValue) {
					Name.UNION, Name.STRUCT -> {
						pos++
						parseStruct(first.srcPos(), struct, potentialName(), n == Name.UNION)
						continue
					}
				}
			}

			val typeNode = parseType()
			val memberName = name()
			val member = MemberNode(Base(first.srcPos(), scope, memberName), typeNode, null)
			context.symTable.add(member)
			struct.members.add(member)
			expectNewline()
		}

		pos++

		return struct
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
			OpNode(Base(srcPos), OpType.MEM, Name.widths[token.nameValue]!!, child, Reg.NONE)
		} else if(token.type == TokenType.LBRACK) {
			pos++
			val child = parseExpr()
			expect(TokenType.RBRACK)
			OpNode(Base(srcPos), OpType.MEM, Width.NONE, child, Reg.NONE)
		} else if(token.type == TokenType.REG) {
			pos++
			val reg = token.regValue
			OpNode(Base(srcPos), reg.type, reg.width, null, reg)
		} else {
			OpNode(Base(srcPos), OpType.IMM, Width.NONE, parseExpr(), Reg.NONE)
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
		return InsNode(Base(srcPos), mnemonic, op1, op2, op3)
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
				InitNode(Base(srcPos), elements)
			}

			TokenType.REG    -> RegNode(Base(srcPos), token.regValue)
			TokenType.NAME   -> NameNode(Base(srcPos), token.nameValue)
			TokenType.INT    -> IntNode(Base(srcPos), token.intValue)
			TokenType.STRING -> StringNode(Base(srcPos), token.stringValue)
			TokenType.CHAR   -> IntNode(Base(srcPos), token.intValue)
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			else             -> UnNode(Base(srcPos), token.type.unOp ?: err(srcPos, "Invalid atom: $token"), parseAtom())
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

		if(atNewline)
			return TypeNode(Base(srcPos), names, emptyList())

		val mods = ArrayList<TypeNode.Mod>()

		while(true) {
			when(tokens[pos].type) {
				TokenType.STAR -> {
					pos++
					mods += TypeNode.PointerMod
				}
				TokenType.LBRACK -> {
					pos++
					if(tokens[pos].type == TokenType.RBRACK) {
						pos++
						mods += TypeNode.ArrayMod(null)
					} else {
						val sizeNode = parseExpr()
						expect(TokenType.RBRACK)
						mods += TypeNode.ArrayMod(sizeNode)
					}
				}
				else -> break
			}
		}

		return TypeNode(Base(srcPos), names, mods)
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
			val base = Base(left.srcPos)
			if(op == BinOp.INV) {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RPAREN) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				left = CallNode(base, left, elements)
			} else {
				val right = parseExpr(op.precedence + 1)
				left = when(op) {
					BinOp.ARR -> ArrayNode(base, left, right).also { expect(TokenType.RBRACK) }
					BinOp.DOT -> DotNode(base, left, right)
					BinOp.REF -> RefNode(base, left, right)
					else      -> BinNode(base, op, left, right)
				}
			}
		}

		return left
	}


}