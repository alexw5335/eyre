package eyre

class Parser(private val context: Context) {


	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<TopNode>

	private lateinit var tokens: List<Token>

	private var lineCount = 0

	private var pos = 0

	private fun id() = tokens[pos++] as? Name ?: err(srcPos(), "Expecting identifier")

	private fun srcPos() = SrcPos(srcFile, lineCount)

	private fun err(srcPos: SrcPos, message: String): Nothing =
		context.err(srcPos, message)

	private fun TopNode.addNode() = nodes.add(this)

	private fun Sym.addSym() = context.symbols.add(this)

	private fun expect(symbol: SymToken) {
		if(tokens[pos++] != symbol)
			err(srcPos(), "Expecting '${symbol.string}'")
	}

	private fun expectNewline() = expect(SymToken.NEWLINE)

	private var currentScope = Scope.NULL

	private val atNewline get() = tokens[pos] == SymToken.NEWLINE

	private fun Name.place() = Place(currentScope, this)

	private fun Name.place(scope: Scope) = Place(scope, this)

	private var currentNamespace: Namespace? = null



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.nodes   = srcFile.nodes
		this.tokens  = srcFile.tokens

		lineCount = 1
		pos = 0

		try {
			parseScope()
		} catch(_: EyreError) {
			srcFile.invalid = true
		}
	}



	private fun parseScope() {
		while(pos < tokens.size) {
			when(val token = tokens[pos]) {
				is Name          -> parseName(token)
				SymToken.NEWLINE -> { pos++; lineCount++ }
				SymToken.RBRACE  -> break
				SymToken.HASH    -> parseDirective()
				SymToken.SEMI    -> pos++
				is SymToken      -> err(srcPos(), "Invalid symbol: ${token.string}")
				else             -> err(srcPos(), "Invalid token: $token")
			}
		}
	}



	private fun parseProc() {
		val srcPos = srcPos()
		pos++
		val name = id()
		val thisScope = currentScope.add(name)
		val proc = Proc(srcPos, name.place(), thisScope)
		proc.addNode()
		proc.addSym()
		expect(SymToken.LBRACE)
		parseScope()
		expect(SymToken.RBRACE)
		ScopeEnd(srcPos(), proc).addNode()
	}



	private fun parseConst() {
		val srcPos = srcPos()
		pos++
		val name = id()
		expect(SymToken.SET)
		val value = parseExpression()
		val const = Const(srcPos, name.place(), value)
		const.addNode()
		const.addSym()
	}



	private fun parseDirective() {
		err(srcPos(), "Directives not yet supported")
	}



	private fun parseNamespace() {
		val srcPos = srcPos()
		pos++
		if(currentNamespace != null)
			err(srcPos, "Only one single-line namespace allowed per file")
		val thisScope = parseScopeName()
		val namespace = Namespace(srcPos, thisScope.last.place(currentScope), thisScope)
		namespace.addNode()
		namespace.addSym()
		currentScope = thisScope
		currentNamespace = namespace
	}



	private fun parseName(name: Name) {
		if(tokens[pos+1] == SymToken.COLON) {
			Label(srcPos(), name.place()).addNode()
			pos += 2
			return
		}

		when(name) {
			Name.NAMESPACE    -> parseNamespace()
			Name.PROC         -> parseProc()
			Name.CONST        -> parseConst()
			in Name.mnemonics -> parseIns(Name.mnemonics[name]!!).addNode()
			else              -> err(srcPos(), "Invalid identifier: $name")
		}
	}



	private fun parseScopeName(): Scope {
		var scope = currentScope.add(id())
		while(tokens[pos] == SymToken.PERIOD) {
			pos++
			scope = scope.add(id())
		}
		expectNewline()
		return scope
	}



	private fun parseAtom(): Node {
		val token = tokens[pos++]

		fun invalidToken(): Nothing = err(srcPos(), "unexpected token: $token")

		return when(token) {
			is SymToken -> when(token) {
				SymToken.LPAREN ->
					parseExpression().also { expect(SymToken.RPAREN) }
				else ->
					UnNode(token.unOp ?: invalidToken(), parseAtom())
			}

			is RegToken    -> RegNode(token.value)
			is Name        -> NameNode(token)
			is IntToken    -> IntNode(token.value)
			is StringToken -> StringNode(token.value)
			is CharToken   -> IntNode(token.value.code.toLong())
			else           -> invalidToken()
		}
	}



	private fun parseExpression(precedence: Int = 0): Node {
		var left = parseAtom()

		while(true) {
			val token = tokens[pos]
			val op = (token as? SymToken)?.binOp ?: break
			if(op.precedence < precedence) break
			pos++
			val expression = parseExpression(op.precedence + 1)
			left = BinNode(op, left, expression)
		}

		return left
	}



	private fun parseOperand(srcPos: SrcPos): OpNode {
		var token = tokens[pos]
		var width: Width? = null

		if(token is Name && token in Name.widths) {
			width = Name.widths[token]
			pos++
			token = tokens[pos]
		}

		if(token == SymToken.LBRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RBRACKET)
			return MemNode(width, value)
		}

		if(width != null)
			err(srcPos, "Width specifier not allowed")

		return when(val expression = parseExpression()) {
			is RegNode -> RegNode(expression.value)
			else       -> ImmNode(width, expression)
		}
	}



	private fun parseIns(mnemonic: Mnemonic): Ins {
		val srcPos = srcPos()
		pos++

		var op1: OpNode? = null
		var op2: OpNode? = null
		var op3: OpNode? = null
		var op4: OpNode? = null

		fun commaOrNewline(): Boolean {
			if(atNewline) return false
			if(tokens[pos++] != SymToken.COMMA)
				err(srcPos, "Expecting newline or comma")
			return true
		}

		if(!atNewline) {
			op1 = parseOperand(srcPos)
			if(commaOrNewline()) {
				op2 = parseOperand(srcPos)
				if(commaOrNewline()) {
					op3 = parseOperand(srcPos)
					if(commaOrNewline()) {
						op4 = parseOperand(srcPos)
						expectNewline()
					}
				}
			}
		}

		return Ins(srcPos, mnemonic, op1, op2, op3, op4)
	}


}