package eyre



@JvmInline
value class AutoOps(val value: Int) {

	constructor(
		r1: Int,
		r2: Int,
		r3: Int,
		r4: Int,
		width: Int,
		vsib: Int,
		st0: Int
	) : this(
		(r1 shl R1) or
		(r2 shl R2) or
		(r3 shl R3) or
		(r4 shl R4) or
		(width shl WIDTH) or
		(vsib shl VSIB) or
		(st0 shl ST0)
	)

	val r1    get() = ((value shr R1) and 15)
	val r2    get() = ((value shr R2) and 15)
	val r3    get() = ((value shr R3) and 15)
	val r4    get() = ((value shr R4) and 15)
	val width get() = ((value shr WIDTH) and 15)
	val vsib  get() = ((value shr VSIB) and 3)
	val st0   get() = ((value shr ST0) and 1)

	fun equalsExceptWidth(other: AutoOps) =
		value and WIDTH_MASK == other.value and WIDTH_MASK

	companion object {
		const val R1    = 0
		const val R2    = 4
		const val R3    = 8
		const val R4    = 12
		const val WIDTH = 16 // 4: NONE BYTE WORD DWORD QWORD TWORD XWORD YWORD ZWORD
		const val VSIB  = 20 // 2: NONE X Y Z
		const val ST0   = 22 // 1: NONE/ST/ST0_ST ST_ST0
		const val WIDTH_MASK = -1 xor (15 shl WIDTH)

		val ST     = AutoOps(OpType.ST.ordinal, 0, 0, 0, 0, 0, 0)
		val ST0_ST = AutoOps(OpType.ST.ordinal, OpType.ST.ordinal, 0, 0, 0, 0, 0)
		val ST_ST0 = AutoOps(OpType.ST.ordinal, OpType.ST.ordinal, 0, 0, 0, 0, 1)
	}

	override fun toString() ="AutoOps($r1 $r2 $r3 $r4, width=$width, vsib=$vsib, st0=$st0)"

}



/*value class AutoEnc2(val value: Long) {
	constructor() : this(0L)

	companion object {
		private const val OPCODE  = 0  // 16
		private const val PREFIX  = 16 // 2  NONE 66 F3 F2
		private const val ESCAPE  = 18 // 2  NONE 0F 38 3A
		private const val EXT     = 20 // 4  0 1 2 3 4 5 6 7
		private const val RW      = 24 // 1
		private const val O16     = 25 // 1
		private const val A32     = 26 // 1
		private const val OPREG   = 27 // 1
		private const val OPENC   = 28 // 3  RMV RVM MRV MVR VMR
		private const val OPS     = 32 // 22
	}

}*/



@JvmInline
value class AutoEnc(val value: Long) {

	constructor() : this(0L)
	val isNull get() = value == 0L
	val isNotNull get() = value != 0L
	
	constructor(
		opcode: Int, 
		prefix: Int,
		escape: Int, 
		ext: Int,
		rw: Int,
		o16: Int,
		a32: Int,
		opReg: Int,
		opEnc: Int,
		pseudo: Int,
		ops: Int
	) : this(
		(opcode.toLong() shl OPCODE) or
		(prefix.toLong() shl PREFIX) or
		(escape.toLong() shl ESCAPE) or
		(ext.toLong() shl EXT) or
		(rw.toLong() shl RW) or
		(o16.toLong() shl O16) or
		(a32.toLong() shl A32) or
		(opReg.toLong() shl OPREG) or
		(opEnc.toLong() shl OPENC) or
		(pseudo.toLong() shl PSEUDO) or
		(ops.toLong() shl OPS)
	)

	val opcode  get() = ((value shr OPCODE) and 0xFF).toInt()
	val prefix  get() = ((value shr PREFIX) and 3).toInt()
	val escape  get() = ((value shr ESCAPE) and 3).toInt()
	val ext     get() = ((value shr EXT) and 15).toInt()
	val rw      get() = ((value shr RW) and 1).toInt()
	val o16     get() = ((value shr O16) and 1).toInt()
	val a32     get() = ((value shr A32) and 1).toInt()
	val opReg   get() = ((value shr OPREG) and 1).toInt()
	val opEnc   get() = ((value shr OPENC) and 7).toInt()
	val pseudo  get() = ((value shr PSEUDO) and 63).toInt()
	val ops     get() = ((value shr OPS)).toInt().let(::AutoOps)

	companion object {
		private const val OPCODE  = 0  // 16
		private const val PREFIX  = 16 // 2  NONE 66 F3 F2
		private const val ESCAPE  = 18 // 2  NONE 0F 38 3A
		private const val EXT     = 20 // 4  0 1 2 3 4 5 6 7
		private const val RW      = 24 // 1
		private const val O16     = 25 // 1
		private const val A32     = 26 // 1
		private const val OPREG   = 27 // 1
		private const val OPENC   = 28 // 3  RMV RVM MRV MVR VMR
		private const val PSEUDO  = 32 // 5
		private const val OPS     = 40 // 22
	}

	override fun toString() = buildString {
		fun add(name: String, value: Int) = append("$name=$value ")
		add("opcode", opcode)
		add("prefix", prefix)
		add("escape" , escape)
		add("ext", ext)
		add("rw", rw)
		add("o16", o16)
		add("a32", a32)
		add("opReg", opReg)
		add("opEnc", opEnc)
		add("pseudo", pseudo)
		add("r1", ops.r1)
		add("r2", ops.r2)
		add("r3", ops.r3)
		add("r4", ops.r4)
		add("width", ops.width)
		add("vsib", ops.vsib)
	}

}