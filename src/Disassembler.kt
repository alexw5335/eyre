package eyre

class Disassembler {


	private val ins = ArrayList<Ins>()

	private lateinit var data: ByteArray

	private var pos = 0

	private val eos get() = pos >= data.size



	fun disassemble(data: ByteArray): List<Ins> {
		ins.clear()
		this.data = data

		while(pos < data.size) {
			val byte = data[pos].toInt()

			when(byte) {
				0x00 -> { }
			}
		}

		return ins
	}


}