package eyre

class Relocation(
	val pos     : Int,
	val section : Section,
	val width   : Width,
	val node    : AstNode,
	val offset  : Int,
	val type    : Type
) {

	enum class Type {
		ABSOLUTE,
		RIP_RELATIVE,
		DEFAULT
	}

}