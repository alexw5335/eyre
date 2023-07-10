package eyre.util

import eyre.ProcSymbol
import eyre.Section
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

object Util {


	fun run(vararg params: String) {
		val process = Runtime.getRuntime().exec(params)
		val reader = BufferedReader(InputStreamReader(process.inputStream))

		while(true) println(reader.readLine() ?: break)

		process.errorReader().readText().let {
			if(it.isNotEmpty()) {
				print("\u001B[31m$it\\u001B[0m")
				error("Process failed")
			}
		}

		process.waitFor()
	}



	fun nasmAssemble(code: String): ByteArray {
		fun ByteArray.int32(pos: Int) =
			this[pos].toInt() or
				(this[pos + 1].toInt() shl 8) or
				(this[pos + 2].toInt() shl 16) or
				(this[pos + 3].toInt() shl 24)

		val temp = Files.createFile(Paths.get("nasmTemp.asm"))
		temp.writeText(code)
		try {
			run("nasm", "-fwin64", "nasmTemp.asm", "-o", "nasmTemp.obj")
		} finally {
			Files.delete(temp)
		}
		val path = Paths.get("nasmTemp.obj")
		val bytes = Files.readAllBytes(path)
		Files.delete(path)
		return bytes.copyOfRange(bytes.int32(40), bytes.int32(40) + bytes.int32(36))
	}


}