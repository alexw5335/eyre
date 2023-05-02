package eyre

import eyre.util.NativeWriter



fun NativeWriter.writeWidth(width: Width, value: Int): Boolean {
	if(value !in width) return false

	when(width) {
		Width.BYTE  -> i8(value)
		Width.WORD  -> i16(value)
		Width.DWORD -> i32(value)
		else        -> return false
	}

	return true
}



fun NativeWriter.writeWidth(width: Width, value: Long): Boolean {
	if(value !in width) return false

	when(width) {
		Width.BYTE  -> i8(value.toInt())
		Width.WORD  -> i16(value.toInt())
		Width.DWORD -> i32(value.toInt())
		Width.QWORD -> i64(value)
		else        -> error("Invalid width")
	}

	return true
}



val Long.isImm8 get() = this in Byte.MIN_VALUE..Byte.MAX_VALUE

val Long.isImm16 get() = this in Short.MIN_VALUE..Short.MAX_VALUE

val Long.isImm32 get() = this in Int.MIN_VALUE..Int.MAX_VALUE

val Int.isImm8 get() = this in Byte.MIN_VALUE..Byte.MAX_VALUE

val Int.isImm16 get() = this in Short.MIN_VALUE..Short.MAX_VALUE



fun String.ascii64(): Long {
	var value = 0L
	for(i in indices)
		value = value or (this[i].code.toLong() shl (i shl 3))
	return value
}