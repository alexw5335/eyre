package eyre

@Suppress("Unused", "MemberVisibilityCanBePrivate")
class BinReader(val bytes: ByteArray) {


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
	Primitives
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

	fun s16b(pos: Int) = s16(pos).swapEndian
	fun u16b(pos: Int) = u16(pos).swapEndian
	fun i32b(pos: Int) = i32(pos).swapEndian
	fun i64b(pos: Int) = i64(pos).swapEndian
	fun f32b(pos: Int) = f32(pos).swapEndian
	fun f64b(pos: Int) = f64(pos).swapEndian

	fun s16b() = s16().swapEndian
	fun u16b() = u16().swapEndian
	fun i32b() = i32().swapEndian
	fun i64b() = i64().swapEndian
	fun f32b() = f32().swapEndian
	fun f64b() = f64().swapEndian



	/*
	Arrays
	 */



	fun bytes(pos: Int, dst: ByteArray, dstPos: Int, length: Int): ByteArray {
		System.arraycopy(bytes, pos, dst, dstPos, length)
		return dst
	}

	fun bytes(dst: ByteArray, dstPos: Int, length: Int): ByteArray {
		System.arraycopy(bytes, pos, dst, dstPos, length)
		pos += length
		return dst
	}

	fun bytes(pos: Int, length: Int) = bytes.copyOfRange(pos, pos + length)

	fun bytes(length: Int) = bytes.copyOfRange(pos, pos + length)

	fun bytesFromPos() = bytes.copyOfRange(pos, bytes.size)



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