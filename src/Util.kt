package eyre

object Util {


	fun run(vararg params: String) {
		val process = Runtime.getRuntime().exec(params)
		val outBuilder = StringBuilder()
		val errBuilder = StringBuilder()

		val outThread = Thread {
			val reader = process.inputReader()
			while(true) outBuilder.appendLine(reader.readLine() ?: break)
		}

		val errThread = Thread {
			val reader = process.errorReader()
			while(true) errBuilder.appendLine(reader.readLine() ?: break)
		}

		outThread.start()
		errThread.start()

		process.waitFor()

		if(outBuilder.isNotEmpty())
			print(outBuilder)

		if(errBuilder.isNotEmpty()) {
			print("\u001B[31m")
			print(errBuilder)
			print("\u001B[0m")
			error("Process failed")
		}
	}


}