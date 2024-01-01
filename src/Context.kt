package eyre

import java.nio.file.Path

class Context(val buildDir: Path, val files: List<SrcFile>) {


	val errors = ArrayList<EyreError>()

	val strings = ArrayList<String>()

	val symTable = HashMap<PlaceKey, Symbol>()



	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	fun err(message: String, srcPos: SrcPos? = null): Nothing =
		throw EyreError(srcPos, message).also(errors::add)


}