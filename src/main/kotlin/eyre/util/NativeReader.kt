package eyre.util

import eyre.swapEndian

@Suppress("Unused", "MemberVisibilityCanBePrivate")
class NativeReader(val bytes: ByteArray) {


	/*
	Position
	 */



	var pos = 0

	fun<T> T.advance(count: Int): T {
		pos += count
		return this
	}

	inline fun retainPos(newPos: Int, block: () -> Unit) {
		val pos = this.pos
		this.pos = newPos
		block()
		this.pos = pos
	}

	inline fun retainPos(block: () -> Unit) {
		val pos = this.pos
		block()
		this.pos = pos
	}



	/*
	Little-endian primitives
	 */



	fun s8(pos: Int) = Unsafe.instance.getByte(bytes, 16L + pos).toInt()

	fun u8(pos: Int) = Unsafe.instance.getByte(bytes, 16L + pos).toUByte().toInt()

	fun s16(pos: Int) = Unsafe.instance.getShort(bytes, 16L + pos).toInt()

	fun u16(pos: Int) = Unsafe.instance.getShort(bytes, 16L + pos).toUShort().toInt()

	fun i32(pos: Int) = Unsafe.instance.getInt(bytes, 16L + pos)

	fun i64(pos: Int) = Unsafe.instance.getLong(bytes, 16L + pos)

	fun f32(pos: Int) = Unsafe.instance.getFloat(bytes, 16L + pos)

	fun f64(pos: Int) = Unsafe.instance.getDouble(bytes, 16L + pos)



	fun s8() = Unsafe.instance.getByte(bytes, 16L + pos).toInt().advance(1)

	fun u8() = Unsafe.instance.getByte(bytes, 16L + pos).toUByte().toInt().advance(1)

	fun s16() = Unsafe.instance.getShort(bytes, 16L + pos).toInt().advance(2)

	fun u16() = Unsafe.instance.getShort(bytes, 16L + pos).toUShort().toInt().advance(2)

	fun i32() = Unsafe.instance.getInt(bytes, 16L + pos).advance(4)

	fun i64() = Unsafe.instance.getLong(bytes, 16L + pos).advance(8)

	fun f32() = Unsafe.instance.getFloat(bytes, 16L + pos).advance(4)

	fun f64() = Unsafe.instance.getDouble(bytes, 16L + pos).advance(8)



	/*
	Big-endian primitives
	 */



	fun s16BE(pos: Int) = Unsafe.instance.getShort(bytes, 16L + pos).toInt().swapEndian

	fun u16BE(pos: Int) = Unsafe.instance.getShort(bytes, 16L + pos).toUShort().toInt().swapEndian

	fun i32BE(pos: Int) = Unsafe.instance.getInt(bytes, 16L + pos).swapEndian

	fun i64BE(pos: Int) = Unsafe.instance.getLong(bytes, 16L + pos).swapEndian

	fun f32BE(pos: Int) = Unsafe.instance.getFloat(bytes, 16L + pos).swapEndian

	fun f64BE(pos: Int) = Unsafe.instance.getDouble(bytes, 16L + pos).swapEndian



	fun s8BE() = Unsafe.instance.getByte(bytes, 16L + pos).toInt().swapEndian.advance(1)

	fun u8BE() = Unsafe.instance.getByte(bytes, 16L + pos).toUByte().toInt().swapEndian.advance(1)

	fun s16BE() = Unsafe.instance.getShort(bytes, 16L + pos).toInt().swapEndian.advance(2)

	fun u16BE() = Unsafe.instance.getShort(bytes, 16L + pos).toUShort().toInt().swapEndian.advance(2)

	fun i32BE() = Unsafe.instance.getInt(bytes, 16L + pos).swapEndian.advance(4)

	fun i64BE() = Unsafe.instance.getLong(bytes, 16L + pos).swapEndian.advance(8)

	fun f32BE() = Unsafe.instance.getFloat(bytes, 16L + pos).swapEndian.advance(4)

	fun f64BE() = Unsafe.instance.getDouble(bytes, 16L + pos).swapEndian.advance(8)



	/*
	Arrays
	 */



	fun bytes(pos: Int, dst: ByteArray, dstPos: Int, length: Int): ByteArray {
		Unsafe.instance.copyMemory(bytes, 16L + pos, dst, 16L + dstPos, length.toLong())
		return dst
	}

	fun bytes(dst: ByteArray, dstPos: Int, length: Int): ByteArray {
		Unsafe.instance.copyMemory(bytes, 16L + pos, dst, 16L + dstPos, length.toLong())
		pos += length
		return dst
	}

	fun bytes(pos: Int, length: Int): ByteArray {
		val bytes = ByteArray(length)
		Unsafe.instance.copyMemory(bytes, 16L + pos, bytes, 16L, length.toLong())
		return bytes
	}

	fun bytes(length: Int): ByteArray {
		val bytes = ByteArray(length)
		Unsafe.instance.copyMemory(bytes, 16L + pos, bytes, 16L, length.toLong())
		pos += length
		return bytes
	}



	/*
	Strings
	 */



	fun ntLength(maxLength: Int = Int.MAX_VALUE): Int {
		for(length in 0 until maxLength)
			if(bytes[pos + length] == 0.toByte())
				return length
		return maxLength
	}



	fun ascii(pos: Int, length: Int) = String(bytes, pos, length, Charsets.US_ASCII)

	fun ascii(length: Int) = ascii(pos, length).advance(length)

	fun asciiNt(pos: Int) = ascii(pos, ntLength())

	fun asciiNt() = ascii(ntLength())



	fun utf8(pos: Int, length: Int) = String(bytes, pos, length, Charsets.UTF_8)

	fun utf8(length: Int) = utf8(pos, length).advance(length)

	fun utf8Nt(pos: Int) = utf8(pos, ntLength())

	fun utf8Nt() = utf8(ntLength())


}