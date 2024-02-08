package eyre

import java.util.*
import kotlin.math.max

class Resolver(private val context: Context) {


	private var scopeStack = Stack<Sym>()



	fun resolve() {
		context.files.forEach(::resolveNodesInFile)
	}

	private fun pushScope(scope: Sym) = scopeStack.push(scope)

	private fun popScope() = scopeStack.pop()

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(srcPos, message)

	private fun visit(file: SrcFile, block: (Node) -> Unit) {
		for(node in file.nodes) {
			try {
				block(node)
			} catch(e: EyreError) {
				file.invalid = true
				break
			}
		}
	}



	private fun resolveNodesInFile(file: SrcFile) {
		if(file.resolved) return
		scopeStack.clear()
		file.resolving = true
		visit(file, ::resolveNodeType)
		visit(file, ::resolveNode)
		file.resolving = false
		file.resolved = true
	}



	private fun resolveNodeFile(srcNode: Node, node: Node) {
		val file = node.srcPos?.file ?: context.internalErr()
		if(file.resolving)
			err(srcNode.srcPos, "Cyclic resolution")
		resolveNodesInFile(file)
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
	Type resolution
	 */



	private fun resolveType(node: TypeNode): Type {
		var type = resolveNames(node.srcPos, node.names) as? Type
			?: err(node.srcPos, "Invalid type")
		for(i in node.arraySizes.indices)
			type = ArrayType(type)
		node.type = type
		return type
	}



	private fun resolveNodeType(node: Node) { when(node) {
		is NamespaceNode -> {
			pushScope(node)
			node.children.forEach(::resolveNodeType)
			popScope()
		}

		is ProcNode -> {
			pushScope(node)
			node.children.forEach(::resolveNodeType)
			popScope()
		}

		is TypedefNode -> {
			node.type = resolveType(node.typeNode ?: context.internalErr())
		}

		is VarNode -> {
			if(node.typeNode != null)
				node.type = resolveType(node.typeNode)
			else if(node.valueNode is StringNode)
				node.type = StringType(node.valueNode.value.length)
			else
				err(node.srcPos, "Cannot infer variable type")
			node.size = node.type.size
		}

		is StructNode -> {
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
		}

		else -> return
	}}




	/*
	Reference resolution
	 */



	private fun resolveTypeNode(node: TypeNode) {
		var type = node.type
		for(i in node.arraySizes.size - 1 downTo 0) {
			val array = type as ArrayType
			resolveNode(node.arraySizes[i])
			val count = resolveInt(node.arraySizes[i])
			if(!count.isImm32) err(node.srcPos, "Array size out of bounds: $count")
			array.count = count.toInt()
			type = array.baseType
		}
	}



	private fun resolveNode(node: Node) { when(node) {
		is NamespaceNode -> {
			pushScope(node)
			node.children.forEach(::resolveNode)
			popScope()
		}
		is NameNode      -> node.sym = resolveName(node.srcPos, node.value)
		is UnNode        -> resolveNode(node.child)
		is DotNode       -> resolveDotNode(node)
		is RefNode       -> resolveRefNode(node)
		is TypedefNode   -> resolveTypeNode(node.typeNode!!)
		is StructNode    -> resolveStruct(node)
		is EnumNode      -> resolveEnum(node)
		is VarNode -> {
			node.typeNode?.let(::resolveTypeNode)
			node.valueNode?.let(::resolveNode)
		}
		is InitNode -> node.elements.forEach(::resolveNode)
		is CallNode -> node.elements.forEach(::resolveNode)
		is BinNode -> {
			resolveNode(node.left)
			resolveNode(node.right)
		}
		is ConstNode -> {
			resolveNode(node.valueNode!!)
			node.intValue = resolveInt(node.valueNode)
			node.resolved = true
		}
		is OpNode -> {
			if(node.child is StringNode)
				context.stringLiterals.add(node.child)
			else if(node.child != null)
				resolveNode(node.child)
		}
		is ProcNode -> {
			if(node.name == Name.MAIN)
				context.entryPoint = node
			pushScope(node)
			node.children.forEach(::resolveNode)
			popScope()
		}
		is InsNode  -> {
			node.op1?.let(::resolveNode)
			node.op2?.let(::resolveNode)
			node.op3?.let(::resolveNode)
		}
		is StringNode,
		is DllCallNode,
		is LabelNode,
		is IntNode  -> Unit
		else -> context.internalErr("Unhandled node: $node")
	}}



	private fun resolveInt(node: Node): Long {
		fun sym(sym: Sym?): Long {
			if(sym == null)
				err(node.srcPos, "Unresolved symbol")
			if(!sym.resolved)
				if(sym is Node)
					resolveNodeFile(node, sym)
				else
					context.internalErr()
			if(sym is IntSym)
				return sym.intValue
			err(node.srcPos, "Invalid int node: $node, $sym")
		}

		return when(node) {
			is IntNode  -> node.value
			is UnNode   -> node.calc(::resolveInt)
			is BinNode  -> node.calc(::resolveInt)
			is NameNode -> sym(node.sym)
			is DotNode  -> sym(node.sym)
			is RefNode  -> {
				if(!node.receiver!!.resolved)
					resolveNodeFile(node, node.receiver!! as Node)
				node.intSupplier?.invoke() ?: err(node.srcPos, "Invalid int node: $node")
			}
			else        -> err(node.srcPos, "Invalid int node: $node")
		}
	}


	private fun resolveDotNode(node: DotNode): Sym {
		val receiver = resolveSymNode(node.left)
		if(node.right !is NameNode)
			err(node.right.srcPos, "Invalid name node: ${node.right}")
		val sym = resolveName(node.srcPos, receiver, node.right.value)
		node.sym = sym
		return sym
	}


	private fun resolveSymNode(node: Node): Sym = when(node) {
		is NameNode -> resolveName(node.srcPos, node.value).also { node.sym = it }
		is DotNode  -> resolveDotNode(node)
		else        -> err(node.srcPos, "Invalid receiver node: $node")
	}

	private fun resolveRefNode(node: RefNode) {
		val right = (node.right as? NameNode)?.value
			?: err(node.right.srcPos, "Invalid reference node: ${node.right}")

		val receiver = resolveSymNode(node.left)
		node.receiver = receiver

		fun invalid(): Nothing = err(node.srcPos, "Invalid reference")

		when(right) {
			Name.COUNT -> when(receiver) {
				is EnumNode -> node.intSupplier = { receiver.entries.size.toLong() }
				else -> invalid()
			}
			Name.SIZE -> when(receiver) {
				is StructNode  -> node.intSupplier = { receiver.size.toLong() }
				is VarNode     -> node.intSupplier = { receiver.size.toLong() }
				is EnumNode    -> node.intSupplier = { receiver.size.toLong() }
				is TypedefNode -> node.intSupplier = { receiver.size.toLong() }
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
				member.offset = structSize
				structSize = (structSize + type.alignment - 1) and -type.alignment
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