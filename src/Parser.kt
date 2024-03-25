package eyre

class Parser(private val context: Context) {


	private lateinit var file: SrcFile

	private lateinit var tokens: List<Token>

	private lateinit var nodes: ArrayList<Node>

	private var pos = 0

	private var scope: Sym? = null

	private var fileNamespace: NamespaceNode? = null

	private var currentFun: FunNode? = null



	/*
	Util
	 */



	private fun<T : Node> T.addNode(): T {
		nodes.add(this)
		return this
	}

	private fun<T> T.addNodeSym(): T where T : Node, T : Sym {
		nodes.add(this)
		context.symTable.add(this)
		return this
	}

	private fun<T : Sym> T.addSym(): T {
		context.symTable.add(this)
		return this
	}

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

	private fun err(node: Node, message: String): Nothing =
		throw EyreError(node.srcPos, message)

	private fun err(message: String): Nothing =
		throw EyreError(tokens[pos].srcPos(), message)

	private fun Token.srcPos() = SrcPos(file, line)

	private fun srcPos() = SrcPos(file, tokens[pos].line)

	private fun name(): Name {
		if(tokens[pos].type != TokenType.NAME)
			err("Expecting name, found: ${tokens[pos].type}")
		return tokens[pos++].nameValue
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



	/*
	Parsing
	 */



	fun parse(file: SrcFile, tokens: List<Token>) {
		this.file = file
		this.tokens = tokens
		this.nodes = file.nodes
		this.pos = 0
		this.fileNamespace = null

		try {
			parseScopeInner()
		} catch(error: EyreError) {
			file.invalid = true
			context.errors.add(error)
		}

		fileNamespace?.let { ScopeEndNode(it).addNode() }
	}



	private fun parseScopeInner() {
		while(pos < tokens.size) {
			when(tokens[pos].type) {
				TokenType.DLLIMPORT -> parseDllImport()
				TokenType.ENUM      -> parseEnum()
				TokenType.UNION     -> parseStruct(scope, true).addNodeSym()
				TokenType.STRUCT    -> parseStruct(scope, false).addNodeSym()
				TokenType.VAR       -> parseVar()
				TokenType.FUN       -> parseFun()
				TokenType.CONST     -> parseConst()
				TokenType.NAMESPACE -> parseNamespace()
				TokenType.NAME      -> handleName()
				TokenType.RBRACE    -> break
				TokenType.EOF       -> break
				else                -> err("Invalid token: ${tokens[pos]}")
			}
		}
	}



	/*private fun parseStatement() {
		when(tokens[pos].type) {
			//TokenType.IF   -> parseIf()
			TokenType.NAME -> handleName()
			else -> err("Invalid token: ${tokens[pos]}")
		}
	}



	private fun parseScopeOrExpr(scope: Sym) {
		val prevScope = this.scope
		this.scope = scope

		if(tokens[pos].type == TokenType.LBRACE) {
			pos++
			parseScopeInner()
			expect(TokenType.RBRACE)
		} else {
			parseStatement()
		}

		ScopeEndNode(scope).addNode()
		this.scope = prevScope
	}*/



	private fun parseScope(scope: Sym) {
		expect(TokenType.LBRACE)
		val prevScope = this.scope
		this.scope = scope
		parseScopeInner()
		this.scope = prevScope
		ScopeEndNode(scope).addNode()
		expect(TokenType.RBRACE)
	}



	private fun handleName() {
		parseExpr().addNode()
		//when(val expr = parseExpr()) {
		//	is CallNode,
		//	is BinNode -> expr.addNode()
		//	else -> err(expr, "Invalid node: $expr")
		//}
	}



	private fun parseDllImport() {
	/*	val srcPos = tokens[pos++].srcPos()
		val dllName = name()
		expect(TokenType.DOT)
		val name = name()
		expectNewline()
		val import = context.getDllImport(dllName, name)
		DllImportNode(NodeInfo(srcPos, scope, name), dllName, import).addNodeSym()*/
	}



	private fun parseEnum() {
		val enumSrcPos = tokens[pos++].srcPos()
		val enum = EnumNode(scope, name())
		enum.srcPos = enumSrcPos
		enum.addNodeSym()
		expect(TokenType.LBRACE)

		while(true) {
			if(tokens[pos].type == TokenType.RBRACE)
				break
			val entrySrcPos = tokens[pos].srcPos()
			val entryName = name()

			val valueNode = if(tokens[pos].type == TokenType.SET) {
				pos++
				parseExpr()
			} else
				null

			val entry = EnumEntryNode(enum, entryName, valueNode)
			entry.srcPos = entrySrcPos
			entry.addSym()
			enum.entries.add(entry)

			if(tokens[pos].type == TokenType.COMMA)
				pos++
		}

		pos++
	}



	private fun parseStruct(parent: Sym?, isUnion: Boolean): StructNode {
		val structSrcPos = tokens[pos++].srcPos()

		val name = if(tokens[pos].type == TokenType.NAME)
			tokens[pos++].nameValue
		else
			Name.NONE

		val struct = StructNode(parent, name, isUnion)
		struct.srcPos = structSrcPos
		val scope: Sym

		if(parent is StructNode) {
			val member = MemberNode(parent, struct.name, null, struct)
			member.srcPos = structSrcPos // ???
			parent.members.add(member)
			scope = parent
		} else {
			if(name.isNull)
				err(structSrcPos, "Top-level struct cannot be anonymous")
			context.symTable.add(struct)
			scope = struct
		}

		expect(TokenType.LBRACE)

		while(tokens[pos].type != TokenType.RBRACE) {
			when(tokens[pos].type) {
				TokenType.UNION -> parseStruct(scope, true)
				TokenType.STRUCT -> parseStruct(scope, false)
				else -> {
					val memberSrcPos = srcPos()
					val memberTypeNode = parseType()
					val memberName = name()
					val member = MemberNode(scope, memberName, memberTypeNode, null)
					member.srcPos = memberSrcPos
					member.addSym()
					struct.members.add(member)
					expectNewline()
				}
			}
		}
		pos++
		return struct
	}



	private fun parseVar() {
		val srcPos = tokens[pos++].srcPos()
		val name = name()
		val typeNode: TypeNode?

		if(tokens[pos].type == TokenType.COLON) {
			pos++
			typeNode = parseType()
		} else {
			typeNode = null
		}

		val valueNode = if(tokens[pos].type == TokenType.SET) {
			pos++
			parseExpr()
		} else null

		if(valueNode is InitNode && typeNode != null && typeNode.mods.size == 1) {
			val mod = typeNode.mods[0]
			if(mod is TypeNode.ArrayMod && mod.sizeNode == null)
				mod.inferredSize = valueNode.elements.size
		}

		val node = VarNode(scope, name, typeNode, valueNode)
		node.srcPos = srcPos
		node.addNodeSym()
		currentFun?.locals?.add(node)
	}



	private fun parseFun() {
		val funSrcPos = tokens[pos++].srcPos()
		val name = name()
		if(currentFun != null)
			err(funSrcPos, "Nested functions not supported")
		val node = FunNode(scope, name)
		node.srcPos = funSrcPos
		node.addNodeSym()
		expect(TokenType.LPAREN)
		while(true) {
			if(tokens[pos].type == TokenType.RPAREN) {
				pos++
				break
			}
			val paramSrcPos = srcPos()
			val paramName = name()
			expect(TokenType.COLON)
			val paramType = parseType()
			val param = VarNode(node, paramName, paramType, null)
			param.srcPos = paramSrcPos
			param.addSym()
			node.params.add(param)
			if(tokens[pos].type != TokenType.COMMA) {
				expect(TokenType.RPAREN)
				break
			} else {
				pos++
			}
		}
		node.returnTypeNode = if(tokens[pos].type == TokenType.COLON) {
			pos++
			parseType()
		} else null
		currentFun = node
		parseScope(node)
		currentFun = null
	}



	private fun parseConst() {
		val srcPos = tokens[pos++].srcPos()
		val name = name()
		expect(TokenType.SET)
		val valueNode = parseExpr()
		val const = ConstNode(scope, name, valueNode)
		const.srcPos = srcPos
		const.addNodeSym()
		expectNewline()
	}



	private fun parseNamespace() {
		val srcPos = tokens[pos++].srcPos()
		var node = NamespaceNode(scope, name())
		node.srcPos = srcPos
		node.addSym()
		while(tokens[pos].type == TokenType.DOT) {
			pos++
			node = NamespaceNode(node, name())
			node.srcPos = srcPos
			node.addSym()
		}
		node.addNode()

		if(tokens[pos].type == TokenType.LBRACE) {
			pos++
			parseScope(node)
		} else {
			if(scope != null)
				err(srcPos, "Single-line namespace must be top-level")
			if(fileNamespace != null)
				err(srcPos, "Only one single-line namespace allowed per file")
			scope = node
			fileNamespace = node
		}
	}



	/*
	Expression parsing
	 */



	private fun parseType(): TypeNode {
		val srcPos = tokens[pos].srcPos()
		val names = ArrayList<Name>()

		do {
			names.add(name())
		} while(tokens[pos++].type == TokenType.DOT)
		pos--

		if(atNewline)
			return TypeNode(names, emptyList()).also { it.srcPos = srcPos }

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

		return TypeNode(names, mods).also { it.srcPos = srcPos }
	}



	private fun parseAtom(precedence: Int = 0): Node {
		val token = tokens[pos++]
		val srcPos = token.srcPos()

		val node = when(token.type) {
			TokenType.LBRACE -> {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RBRACE) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				InitNode(elements)
			}
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			TokenType.NAME   -> NameNode(token.nameValue)
			TokenType.INT    -> IntNode(token.intValue)
			TokenType.STRING -> StringNode(token.stringValue)
			TokenType.CHAR   -> IntNode(token.intValue)
			else -> {
				val op = token.type.unOp ?: err(srcPos, "Invalid atom: $token")
				//if(op.precedence < precedence) { }
				val child = parseExpr(op.precedence)
				UnNode(op, child)
			}
		}

		node.srcPos = srcPos

		return when(tokens[pos].type.unOp) {
			UnOp.INC_PRE -> UnNode(UnOp.INC_POST, node).also { it.srcPos = srcPos; pos++ }
			UnOp.DEC_PRE -> UnNode(UnOp.DEC_POST, node).also { it.srcPos = srcPos; pos++ }
			else         -> node
		}
	}



	private fun parseExpr(precedence: Int = 0): Node {
		var left = parseAtom(precedence)

		while(true) {
			val token = tokens[pos]

			if(token.type == TokenType.ELSE || token.type == TokenType.ELIF)
				break

			if(!token.type.isSym)
				if(atNewline)
					break
				else
					err(left, "Invalid binary operator token: $token")

			val op = token.type.binOp ?: break
			if(op.precedence < precedence) break
			pos++

			val srcPos = left.srcPos

			if(op == BinOp.CALL) {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RPAREN) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				left = CallNode(left, elements)
				currentFun?.let {
					if(elements.size > it.mostParams)
						it.mostParams = elements.size
				}
			} else {
				val right = parseExpr(op.precedence + 1)
				left = when(op) {
					BinOp.ARR -> ArrayNode(left, right).also { expect(TokenType.RBRACK) }
					BinOp.DOT -> DotNode(left, right)
					BinOp.REF -> RefNode(left, right)
					else      -> BinNode(op, left, right)
				}
			}

			left.srcPos = srcPos
		}

		return left
	}


}