package eyre

import java.util.*
import kotlin.math.max

/**
 * The resolver has two stages:
 * - Type resolution
 * - Symbol resolution and constant calculation
 */
class Resolver(private val context: Context) {


	private var scopeStack = Stack<Sym>()

	private fun pushScope(scope: Sym) = scopeStack.push(scope)

	private fun popScope() = scopeStack.pop()

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

	private fun err(node: Node, message: String): Nothing =
		throw EyreError(node.srcPos, message)



	fun resolveFile(file: SrcFile) {
		if(file.resolved || file.invalid) return
		scopeStack.clear()
		file.resolving = true

		try {
			for(node in file.nodes) resolveNodeType(node)
			for(node in file.nodes) resolveNode(node)
		} catch(e: EyreError) {
			file.invalid = true
			context.errors.add(e)
			return
		}

		file.resolving = false
		file.resolved = true
	}



	private fun resolveNodeFile(srcNode: Node, node: Node) {
		val file = node.srcPos?.file ?: context.internalErr()
		if(file.resolving)
			err(srcNode.srcPos, "Cyclic resolution")
		resolveFile(file)
	}



	/*
	Name resolution
	 */



	private fun resolveNames(srcPos: SrcPos?, names: List<Name>): Sym {
		var sym = resolveName(srcPos, names[0])
		for(i in 1 ..< names.size) {
			sym = resolveName(srcPos, sym, names[i])
		}
		return sym
	}



	private fun resolveName(srcPos: SrcPos?, scope: Sym, name: Name): Sym {
		return context.symTable.get(scope, name)
			?: err(srcPos, "Unresolved symbol: $name")
	}



	private fun resolveName(srcPos: SrcPos?, name: Name): Sym {
		context.symTable.get(null, name)?.let { return it }
		for(i in scopeStack.indices.reversed())
			context.symTable.get(scopeStack[i], name)?.let { return it }
		err(srcPos, "Unresolved symbol: $name")
	}



	/*
	Type resolution (stage 1)
	 */



	private fun resolveType(node: TypeNode): Type {
		var type = resolveNames(node.srcPos, node.names) as? Type
			?: err(node.srcPos, "Invalid type")
		for(mod in node.mods) {
			type = when(mod) {
				is TypeNode.PointerMod -> PointerType(type)
				is TypeNode.ArrayMod -> ArrayType(type)
			}
		}
		node.type = type
		return type
	}



	private fun resolveNodeType(node: Node) { when(node) {
		is ScopeEndNode  -> popScope()
		is NamespaceNode -> pushScope(node)
		is IfNode        -> pushScope(node)
		is ProcNode      -> pushScope(node)
		is DoWhileNode   -> pushScope(node)
		is WhileNode     -> pushScope(node)
		is ForNode       -> pushScope(node)

		is VarNode ->
			if(node.typeNode != null)
				node.type = resolveType(node.typeNode)
			else if(node.valueNode is StringNode)
				node.type = StringType(node.valueNode.value.length)
			else
				err(node.srcPos, "Cannot infer variable type")

		is StructNode ->
			for(member in node.members) {
				if(member.struct != null) {
					resolveNodeType(member.struct)
					member.type = member.struct
				} else if(member.typeNode != null) {
					member.type = resolveType(member.typeNode)
				} else {
					context.internalErr()
				}
			}

		else -> return
	}}




	/*
	Reference resolution
	 */



	private fun resolveTypeNode(node: TypeNode) {
		var type = node.type
		for(i in node.mods.size - 1 downTo 0) {
			when(val mod = node.mods[i]) {
				is TypeNode.PointerMod -> type = (type as PointerType).type
				is TypeNode.ArrayMod -> {
					val array = type as ArrayType

					if(mod.sizeNode == null) {
						if(mod.inferredSize == -1)
							err(node.srcPos, "Array size required")
						array.count = mod.inferredSize
					} else {
						resolveNode(mod.sizeNode)
						val count = resolveInt(mod.sizeNode)
						if(!count.isImm32) err(node.srcPos, "Array size out of bounds: $count")
						array.count = count.toInt()
						type = array.type
					}
				}
			}
		}
	}



	private fun resolveNode(node: Node) { when(node) {
		is ScopeEndNode -> popScope()
		is NamespaceNode -> pushScope(node)
		is NameNode -> node.sym = resolveName(node.srcPos, node.value)
		is UnNode -> resolveNode(node.child)
		is DotNode -> resolveDotNode(node)
		is ArrayNode -> resolveArrayNode(node)
		is RefNode -> resolveRefNode(node)
		is StructNode -> resolveStruct(node)
		is EnumNode -> resolveEnum(node)
		is VarNode -> {
			node.typeNode?.let(::resolveTypeNode)
			node.valueNode?.let(::resolveNode)
			if(node.type is StringType) {
				if(node.valueNode !is StringNode)
					err(node.srcPos, "Invalid string")
				node.size = node.valueNode.value.length
			} else {
				node.size = node.type.size
			}
		}
		is InitNode -> node.elements.forEach(::resolveNode)
		is CallNode -> {
			resolveNode(node.left)
			node.elements.forEach(::resolveNode)
		}
		is BinNode -> {
			resolveNode(node.left)
			resolveNode(node.right)
		}
		is ConstNode -> {
			resolveNode(node.valueNode)
			node.intValue = resolveInt(node.valueNode)
			node.resolved = true
		}
		is ProcNode -> {
			if(node.name == Name.MAIN)
				context.entryPoint = node
			pushScope(node)
		}
		is InsNode -> {
			node.op1?.let(::resolveNode)
			node.op2?.let(::resolveNode)
			node.op3?.let(::resolveNode)
		}
		is DoWhileNode -> {
			pushScope(node)
			resolveNode(node.condition)
		}
		is WhileNode -> {
			pushScope(node)
			resolveNode(node.condition)
		}
		is ForNode -> {
			resolveNode(node.range)
			pushScope(node)
		}
		is IfNode -> {
			node.condition?.let(::resolveNode)
			pushScope(node)
		}
		is StringNode -> {
			val sym = StringLitSym(node.value)
			node.litSym = sym
			context.stringLiterals.add(sym)
		}
		is MemNode -> resolveNode(node.child)
		is ImmNode -> resolveNode(node.child)
		is RegNode,
		is LabelNode,
		is DllImportNode,
		is IntNode -> Unit
		else -> context.internalErr("Unhandled node: $node")
	}}



	private fun resolveInt(node: Node): Long {
		fun sym(sym: Sym?): Long {
			if(sym == null)
				err(node.srcPos, "Unresolved symbol")
			if(!sym.resolved)
				resolveNodeFile(node, sym)
			if(sym is IntSym)
				return sym.intValue
			err(node.srcPos, "Invalid int node: $node, $sym")
		}

		return when(node) {
			is IntNode  -> node.value
			is UnNode   -> node.calc(::resolveInt)
			is BinNode  -> node.calc(::resolveInt)
			is NameNode -> sym(node.sym)
			//is DotNode  -> sym(node.sym)
			is RefNode  -> {
				if(!node.receiver!!.resolved)
					resolveNodeFile(node, node.receiver!! as Node)
				node.intSupplier?.invoke() ?: err(node.srcPos, "Invalid int node: $node")
			}
			else -> err(node.srcPos, "Invalid int node: $node")
		}
	}



	private fun resolveNameNode(node: NameNode): Sym {
		val sym = resolveName(node.srcPos, node.value)
		node.sym = sym
		return sym
	}



	private fun resolveReceiver(node: Node): Sym = when(node) {
		is NameNode  -> resolveNameNode(node)
		is ArrayNode -> resolveArrayNode(node)
		is DotNode   -> resolveDotNode(node)
		else         -> err(node, "Invalid node")
	}



	private fun resolveArrayNode(node: ArrayNode): Type {
		resolveNode(node.left)
		val receiver = resolveReceiver(node.left) as? VarNode
			?: err(node.left, "Invalid receiver")
		node.receiver = receiver
		val type = receiver.type
		node.receiver = receiver
		if(type is ArrayType) {
			node.type = type.type
		} else if(type is PointerType) {
			node.type = type.type
		} else {
			err(node.srcPos, "Invalid type")
		}
		return node.type!!
	}


	// list of operations: member access, member deref, index
	// chain: base var followed by list of operations

	private fun resolveChain(node: Node): Sym {
		var current = node

		val list = ArrayList<Node>()

		while(true) {
			list.add(node)
			current = when(current) {
				is NameNode  -> break
				is DotNode   -> current.left
				is ArrayNode -> current.left
				else         -> err(node, "Invalid node")
			}
		}


	}

	private fun resolveDotNode(node: DotNode): Sym {
		val right = (node.right as? NameNode)?.value
			?: err(node.right, "Expecting name, found: ${node.right}")

		val receiver: Sym = when(node.left) {
			is ArrayNode -> resolveArrayNode(node.left)
			is DotNode   -> resolveDotNode(node.left)
			is NameNode  -> resolveNameNode(node.left)
			else         -> err(node, "Invalid node")
		}

		if(receiver is AccessSym) {
			node.sym = receiver

			if(receiver.type is PointerType) {
				receiver.ops.add()
			}

			return receiver
		} else if(receiver is VarNode) {
			val sym = AccessSym(receiver, receiver.type)
			val type = receiver.type

		} else {
			val sym = resolveName(node.srcPos, receiver, right)
			node.sym = sym
			return sym
		}
	}



	private fun resolveRefNode(node: RefNode) {
		val right = (node.right as? NameNode)?.value
			?: err(node.right.srcPos, "Invalid reference node: ${node.right}")

		val receiver: Sym = when(node.left) {
			is NameNode -> resolveNameNode(node.left)
			is DotNode -> resolveDotNode(node.left)
			else -> err(node.left, "Invalid receiver")
		}

		node.receiver = receiver

		fun invalid(): Nothing = err(node.srcPos, "Invalid reference")

		when(right) {
			Name.OFFSET -> when(receiver) {
				is MemberNode -> { }

			}
			Name.COUNT -> when(receiver) {
				is EnumNode -> node.intSupplier = { receiver.entries.size.toLong() }
				else -> invalid()
			}
			Name.SIZE -> when(receiver) {
				is StructNode  -> node.intSupplier = { receiver.size.toLong() }
				is VarNode     -> node.intSupplier = { receiver.size.toLong() }
				is EnumNode    -> node.intSupplier = { receiver.size.toLong() }
				else -> invalid()
			}
			else -> invalid()
		}
	}


	private fun resolveEnum(enum: EnumNode) {
		pushScope(enum)

		var current = 0L
		var max = 0L

		for(entry in enum.entries) {
			entry.intValue = if(entry.valueNode != null) {
				resolveNode(entry.valueNode)
				resolveInt(entry.valueNode)
			} else {
				current
			}

			max = max.coerceAtLeast(entry.intValue)

			current = entry.intValue + 1
			entry.resolved = true
		}

		enum.resolved = true

		enum.size = when {
			max >= Short.MAX_VALUE -> 4
			max >= Byte.MAX_VALUE -> 2
			else -> 1
		}

		enum.alignment = enum.size

		popScope()
	}



	private fun resolveStruct(struct: StructNode) {
		for(member in struct.members)
			if(member.typeNode != null)
				resolveTypeNode(member.typeNode)
			else
				resolveStruct(member.struct!!)

		var structSize = 0
		var structAlignment = 0

		for((index, member) in struct.members.withIndex()) {
			val type = member.type
			member.index = index

			if(struct.isUnion) {
				member.offset = 0
				structSize = max(structSize, type.size)
			} else {
				structSize = (structSize + type.alignment - 1) and -type.alignment
				member.offset = structSize
				structSize += type.size
			}

			if(structAlignment < 8 && type.alignment > structAlignment)
				structAlignment = type.alignment
		}

		structSize = (structSize + structAlignment - 1) and -structAlignment
		struct.size = structSize
		struct.alignment = structAlignment
	}


}