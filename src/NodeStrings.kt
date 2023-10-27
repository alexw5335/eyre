package eyre

object NodeStrings {


	private fun StringBuilder.appendExpr(node: Node) {
		when(node) {
			is IntNode -> append(node.value.toString())
			is NameNode -> append(node.value.string)
			is RegNode -> append(node.value.string)

			is UnNode -> {
				append('(')
				append(node.op.string)
				appendExpr(node.node)
				append(')')
			}

			is BinNode -> {
				append('(')
				appendExpr(node.left)
				append(' ')
				append(node.op.string)
				append(' ')
				appendExpr(node.right)
				append(')')
			}

			is OpNode -> {
				node.width?.let { append(it.string); append(' ') }

				if(node.node != NullNode) {
					if(node.type == OpType.MEM) {
						append('[')
						appendExpr(node.node)
						append(']')
					} else {
						appendExpr(node.node)
					}
				} else {
					append(node.reg.string)
				}
			}

			else -> error("Invalid expr node: $node")
		}
	}



	fun string(node: Node) = when(node) {
		is InsNode -> buildString {
			append(node.mnemonic)
			if(node.op1.isNone) return@buildString
			append(' ')
			appendExpr(node.op1)
			if(node.op2.isNone) return@buildString
			append(", ")
			appendExpr(node.op2)
			if(node.op3.isNone) return@buildString
			append(", ")
			appendExpr(node.op3)
			if(node.op4.isNone) return@buildString
			append(", ")
			appendExpr(node.op4)
		}

		else -> error("Invalid print string")
	}


}