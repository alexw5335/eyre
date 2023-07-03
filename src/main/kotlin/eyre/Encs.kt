package eyre

import eyre.Mnemonic.*

object Encs {

	val sseEncs = mapOf<Mnemonic, IntArray>(
		PCMPESTRM to intArrayOf(1251680, 7543136),
		PHSUBSW to intArrayOf(2363399, 7606279, 1184007, 7475463),
		PABSB to intArrayOf(2363420, 7606300, 1184028, 7475484),
		MOVDQA to intArrayOf(1182063, 7473519, 68290943, 69077375),
		PABSD to intArrayOf(2363422, 7606302, 1184030, 7475486),
		RSQRTPS to intArrayOf(1181778, 7473234),
		CVTPS2PD to intArrayOf(1181786, 7473242),
		PMULDQ to intArrayOf(1184040, 7475496),
		PSUBW to intArrayOf(2361593, 7604473, 1182201, 7473657),
		CVTPS2PI to intArrayOf(1312813, 7604269),
		AESDEC to intArrayOf(1184222, 7475678),
		CMPEQSD to intArrayOf(1182402, 7473858),
		PCMPESTRI to intArrayOf(1251681, 7543137),
		PSUBQ to intArrayOf(2361595, 7604475, 1182203, 7473659),
		PABSW to intArrayOf(2363421, 7606301, 1184029, 7475485),
		MOVDQU to intArrayOf(1182575, 7474031, 68291455, 69077887),
		CVTTPD2PI to intArrayOf(1313068, 7604524),
		PSHUFW to intArrayOf(2426992, 7669872),
		MOVHLPS to intArrayOf(1181714),
		CMPEQSS to intArrayOf(1182658, 7474114),
		MAXPS to intArrayOf(1181791, 7473247),
		UNPCKHPD to intArrayOf(1181973, 7473429),
		RCPPS to intArrayOf(1181779, 7473235),
		CMPSD to intArrayOf(167, 1247938, 7539394),
		ADDSUBPD to intArrayOf(1182160, 7473616),
		PUNPCKHWD to intArrayOf(2361449, 7604329, 1182057, 7473513),
		PCMPEQQ to intArrayOf(1184041, 7475497),
		MOVSD to intArrayOf(165, 1182224, 7473680, 68291089, 69077521),
		PCMPEQB to intArrayOf(2361460, 7604340, 1182068, 7473524),
		MOVLPD to intArrayOf(69077267, 7473426),
		PCMPEQD to intArrayOf(2361462, 7604342, 1182070, 7473526),
		UNPCKHPS to intArrayOf(1181717, 7473173),
		MOVNTQ to intArrayOf(70125799),
		POR to intArrayOf(2361579, 7604459, 1182187, 7473643),
		PCLMULHQLQDQ to intArrayOf(1186116, 7477572),
		CMPUNORDSS to intArrayOf(1182658, 7474114),
		MOVSS to intArrayOf(1182480, 7473936, 68291345, 69077777),
		PSHUFHW to intArrayOf(1248112, 7539568),
		PSHUFD to intArrayOf(1247600, 7539056),
		SUBSS to intArrayOf(1182556, 7474012),
		AESDECLAST to intArrayOf(1184223, 7475679),
		PACKUSDW to intArrayOf(1184043, 7475499),
		PMADDWD to intArrayOf(2361589, 7604469, 1182197, 7473653),
		PSHUFB to intArrayOf(2363392, 7606272, 1184000, 7475456),
		PCMPEQW to intArrayOf(2361461, 7604341, 1182069, 7473525),
		MOVLPS to intArrayOf(7473170, 69077011),
		CMPLTSS to intArrayOf(1182658, 7474114),
		PCLMULLQHQDQ to intArrayOf(1186116, 7477572),
		ANDPS to intArrayOf(1181780, 7473236),
		MOVUPS to intArrayOf(1181712, 7473168, 68290577, 69077009),
		PMAXUW to intArrayOf(1184062, 7475518),
		MOVDQ2Q to intArrayOf(1313494),
		CVTPI2PD to intArrayOf(2230570, 7473450),
		PCMPISTRI to intArrayOf(1251683, 7543139),
		MINSS to intArrayOf(1182557, 7474013),
		AESIMC to intArrayOf(1184219, 7475675),
		CMPPD to intArrayOf(1247682, 7539138),
		PCMPISTRM to intArrayOf(1251682, 7543138),
		PUNPCKLQDQ to intArrayOf(1182060, 7473516),
		ANDPD to intArrayOf(1182036, 7473492),
		ADDSUBPS to intArrayOf(1182416, 7473872),
		CVTPI2PS to intArrayOf(2230314, 7473194),
		MINSD to intArrayOf(1182301, 7473757),
		PMAXUD to intArrayOf(1184063, 7475519),
		PMAXUB to intArrayOf(2361566, 7604446, 1182174, 7473630),
		PADDSW to intArrayOf(2361581, 7604461, 1182189, 7473645),
		MAXPD to intArrayOf(1182047, 7473503),
		BLENDVPD to intArrayOf(1184021, 7475477),
		HADDPS to intArrayOf(1182332, 7473788),
		PTEST to intArrayOf(1184023, 7475479),
		PMOVZXWQ to intArrayOf(1184052, 7475508),
		MOVUPD to intArrayOf(1181968, 7473424, 68290833, 69077265),
		PADDSB to intArrayOf(2361580, 7604460, 1182188, 7473644),
		PBLENDW to intArrayOf(1251598, 7543054),
		SHA256MSG1 to intArrayOf(1183948, 7475404),
		SHA256MSG2 to intArrayOf(1183949, 7475405),
		RCPSS to intArrayOf(1182547, 7474003),
		CMPPS to intArrayOf(1247426, 7538882),
		CVTTSS2SI to intArrayOf(1706796, 7998252, 18615084, 24906540),
		HADDPD to intArrayOf(1182076, 7473532),
		PMOVZXWD to intArrayOf(1184051, 7475507),
		ROUNDPD to intArrayOf(1251593, 7543049),
		PINSRB to intArrayOf(3348768, 7543072, 5445920),
		CVTPD2PS to intArrayOf(1182042, 7473498),
		PMAXSW to intArrayOf(2361582, 7604462, 1182190, 7473646),
		CVTTPS2PI to intArrayOf(1312812, 7604268),
		INSERTPS to intArrayOf(1251617, 7543073),
		PHSUBW to intArrayOf(2363397, 7606277, 1184005, 7475461),
		CVTPS2DQ to intArrayOf(1182043, 7473499),
		PINSRQ to intArrayOf(23271714, 24320290),
		MULSD to intArrayOf(1182297, 7473753),
		ROUNDPS to intArrayOf(1251592, 7543048),
		PMAXSD to intArrayOf(1184061, 7475517),
		SUBPS to intArrayOf(1181788, 7473244),
		PMAXSB to intArrayOf(1184060, 7475516),
		PINSRD to intArrayOf(5445922, 7543074),
		PAVGB to intArrayOf(2361568, 7604448, 1182176, 7473632),
		PUNPCKHBW to intArrayOf(2361448, 7604328, 1182056, 7473512),
		SUBPD to intArrayOf(1182044, 7473500),
		UCOMISD to intArrayOf(1181998, 7473454),
		CVTPD2PI to intArrayOf(1313069, 7604525),
		PAVGW to intArrayOf(2361571, 7604451, 1182179, 7473635),
		PADDB to intArrayOf(2361596, 7604476, 1182204, 7473660),
		PADDD to intArrayOf(2361598, 7604478, 1182206, 7473662),
		GF2P8MULB to intArrayOf(1184207, 7475663),
		PMULHRSW to intArrayOf(2363403, 7606283, 1184011, 7475467),
		PCLMULHQHQDQ to intArrayOf(1186116, 7477572),
		MPSADBW to intArrayOf(1251650, 7543106),
		PHSUBD to intArrayOf(2363398, 7606278, 1184006, 7475462),
		LDDQU to intArrayOf(7473904),
		UCOMISS to intArrayOf(1181742, 7473198),
		MULPS to intArrayOf(1181785, 7473241),
		PADDW to intArrayOf(2361597, 7604477, 1182205, 7473661),
		PADDQ to intArrayOf(2361556, 7604436, 1182164, 7473620),
		PCMPGTQ to intArrayOf(1184055, 7475511),
		PCMPGTD to intArrayOf(2361446, 7604326, 1182054, 7473510),
		SUBSD to intArrayOf(1182300, 7473756),
		AESKEYGENASSIST to intArrayOf(1251807, 7543263),
		MAXSD to intArrayOf(1182303, 7473759),
		AESENCLAST to intArrayOf(1184221, 7475677),
		CVTDQ2PD to intArrayOf(1182694, 7474150),
		GF2P8AFFINEINVQB to intArrayOf(1251791, 7543247),
		PCMPGTW to intArrayOf(2361445, 7604325, 1182053, 7473509),
		PSIGNW to intArrayOf(2363401, 7606281, 1184009, 7475465),
		PSIGND to intArrayOf(2363402, 7606282, 1184010, 7475466),
		PSIGNB to intArrayOf(2363400, 7606280, 1184008, 7475464),
		CVTTSD2SI to intArrayOf(1706540, 7997996, 18614828, 24906284),
		MOVDDUP to intArrayOf(1182226, 7473682),
		PUNPCKHDQ to intArrayOf(2361450, 7604330, 1182058, 7473514),
		CVTDQ2PS to intArrayOf(1181787, 7473243),
		PHMINPOSUW to intArrayOf(1184065, 7475521),
		PMINUW to intArrayOf(1184058, 7475514),
		PSRLD to intArrayOf(2361554, 7604434, 346226, 1182162, 7473618, 215410),
		ANDNPS to intArrayOf(1181781, 7473237),
		PINSRW to intArrayOf(4524228, 7669956, 5572804, 4393412, 7539140),
		PACKUSWB to intArrayOf(2361447, 7604327, 1182055, 7473511),
		PSRLW to intArrayOf(2361553, 7604433, 346225, 1182161, 7473617, 215409),
		PCMPGTB to intArrayOf(2361444, 7604324, 1182052, 7473508),
		PSRLQ to intArrayOf(2361555, 7604435, 346227, 1182163, 7473619, 215411),
		PSUBD to intArrayOf(2361594, 7604474, 1182202, 7473658),
		MULPD to intArrayOf(1182041, 7473497),
		PSUBB to intArrayOf(2361592, 7604472, 1182200, 7473656),
		ANDNPD to intArrayOf(1182037, 7473493),
		MAXSS to intArrayOf(1182559, 7474015),
		ORPS to intArrayOf(1181782, 7473238),
		CMPNLEPS to intArrayOf(1181890, 7473346),
		BLENDPS to intArrayOf(1251596, 7543052),
		PSADBW to intArrayOf(2361590, 7604470, 1182198, 7473654),
		PSUBSW to intArrayOf(2361577, 7604457, 1182185, 7473641),
		CVTSI2SS to intArrayOf(5376810, 7473962, 23202602, 24251178),
		PMINUB to intArrayOf(2361562, 7604442, 1182170, 7473626),
		CVTPD2DQ to intArrayOf(1182438, 7473894),
		ADDSS to intArrayOf(1182552, 7474008),
		PMINUD to intArrayOf(1184059, 7475515),
		PADDUSW to intArrayOf(2361565, 7604445, 1182173, 7473629),
		SHA1MSG2 to intArrayOf(1183946, 7475402),
		SHA1MSG1 to intArrayOf(1183945, 7475401),
		PBLENDVB to intArrayOf(1184016, 7475472),
		BLENDPD to intArrayOf(1251597, 7543053),
		PMOVSXDQ to intArrayOf(1184037, 7475493),
		CVTSI2SD to intArrayOf(5376554, 7473706, 23202346, 24250922),
		SQRTPS to intArrayOf(1181777, 7473233),
		UNPCKLPD to intArrayOf(1181972, 7473428),
		ADDSD to intArrayOf(1182296, 7473752),
		EXTRACTPS to intArrayOf(68884759, 69146903, 85793047),
		PSUBSB to intArrayOf(2361576, 7604456, 1182184, 7473640),
		PMULLD to intArrayOf(1184064, 7475520),
		PMINSW to intArrayOf(2361578, 7604458, 1182186, 7473642),
		MOVNTDQ to intArrayOf(69077479),
		PADDUSB to intArrayOf(2361564, 7604444, 1182172, 7473628),
		PUNPCKLWD to intArrayOf(2361441, 7604321, 1182049, 7473505),
		SQRTPD to intArrayOf(1182033, 7473489),
		MOVHPD to intArrayOf(69077271, 7473430),
		PHADDSW to intArrayOf(2363395, 7606275, 1184003, 7475459),
		ROUNDSS to intArrayOf(1251594, 7543050),
		CMPORDPS to intArrayOf(1181890, 7473346),
		MOVHPS to intArrayOf(7473174, 69077015),
		UNPCKLPS to intArrayOf(1181716, 7473172),
		MASKMOVDQU to intArrayOf(1182199),
		ORPD to intArrayOf(1182038, 7473494),
		CMPNLEPD to intArrayOf(1182146, 7473602),
		GF2P8AFFINEQB to intArrayOf(1251790, 7543246),
		PMULLW to intArrayOf(2361557, 7604437, 1182165, 7473621),
		CVTSD2SI to intArrayOf(1706541, 7997997, 18614829, 24906285),
		CMPORDPD to intArrayOf(1182146, 7473602),
		PMULHUW to intArrayOf(2361572, 7604452, 1182180, 7473636),
		SHA256RNDS2 to intArrayOf(1183947, 7475403),
		PANDN to intArrayOf(2361567, 7604447, 1182175, 7473631),
		PAND to intArrayOf(2361563, 7604443, 1182171, 7473627),
		PHADDD to intArrayOf(2363394, 7606274, 1184002, 7475458),
		PMOVSXBD to intArrayOf(1184033, 7475489),
		CMPNLTPD to intArrayOf(1182146, 7473602),
		CMPORDSS to intArrayOf(1182658, 7474114),
		HSUBPS to intArrayOf(1182333, 7473789),
		MOVMSKPD to intArrayOf(1706320, 18614608),
		MOVSLDUP to intArrayOf(1182482, 7473938),
		SQRTSD to intArrayOf(1182289, 7473745),
		PMINSD to intArrayOf(1184057, 7475513),
		PMOVSXBW to intArrayOf(1184032, 7475488),
		CMPNLTPS to intArrayOf(1181890, 7473346),
		CVTSS2SI to intArrayOf(1706797, 7998253, 18615085, 24906541),
		PMINSB to intArrayOf(1184056, 7475512),
		CMPLEPS to intArrayOf(1181890, 7473346),
		CMPNLESD to intArrayOf(1182402, 7473858),
		CVTSS2SD to intArrayOf(1182554, 7474010),
		PMOVSXBQ to intArrayOf(1184034, 7475490),
		HSUBPD to intArrayOf(1182077, 7473533),
		CMPORDSD to intArrayOf(1182402, 7473858),
		MOVMSKPS to intArrayOf(1706064, 18614352),
		ROUNDSD to intArrayOf(1251595, 7543051),
		MULSS to intArrayOf(1182553, 7474009),
		PSLLQ to intArrayOf(2361587, 7604467, 378995, 1182195, 7473651, 248179),
		CMPLEPD to intArrayOf(1182146, 7473602),
		DIVSD to intArrayOf(1182302, 7473758),
		MOVAPD to intArrayOf(1181992, 7473448, 68290857, 69077289),
		PMOVMSKB to intArrayOf(2754775, 1706455),
		PSLLD to intArrayOf(2361586, 7604466, 378994, 1182194, 7473650, 248178),
		DIVSS to intArrayOf(1182558, 7474014),
		PALIGNR to intArrayOf(2430991, 7673871, 1251599, 7543055),
		AESENC to intArrayOf(1184220, 7475676),
		MOVAPS to intArrayOf(1181736, 7473192, 68290601, 69077033),
		PHADDW to intArrayOf(2363393, 7606273, 1184001, 7475457),
		PSLLW to intArrayOf(2361585, 7604465, 378993, 1182193, 7473649, 248177),
		CMPLTPS to intArrayOf(1181890, 7473346),
		CMPLESD to intArrayOf(1182402, 7473858),
		XORPS to intArrayOf(1181783, 7473239),
		ADDPD to intArrayOf(1182040, 7473496),
		CMPNEQPS to intArrayOf(1181890, 7473346),
		COMISD to intArrayOf(1181999, 7473455),
		PMULHW to intArrayOf(2361573, 7604453, 1182181, 7473637),
		CMPLESS to intArrayOf(1182658, 7474114),
		CVTTPD2DQ to intArrayOf(1182182, 7473638),
		CMPUNORDPS to intArrayOf(1181890, 7473346),
		PCLMULQDQ to intArrayOf(1251652, 7543108),
		XORPD to intArrayOf(1182039, 7473495),
		CMPLTPD to intArrayOf(1182146, 7473602),
		PMADDUBSW to intArrayOf(2363396, 7606276, 1184004, 7475460),
		CMPNEQPD to intArrayOf(1182146, 7473602),
		BLENDVPS to intArrayOf(1184020, 7475476),
		PSRAD to intArrayOf(2361570, 7604450, 362610, 1182178, 7473634, 231794),
		MOVLHPS to intArrayOf(1181718),
		CMPNLESS to intArrayOf(1182658, 7474114),
		DPPD to intArrayOf(1251649, 7543105),
		CMPUNORDPD to intArrayOf(1182146, 7473602),
		MOVNTDQA to intArrayOf(7475498),
		PSLLDQ to intArrayOf(256371),
		PACKSSWB to intArrayOf(2361443, 7604323, 1182051, 7473507),
		PSRAW to intArrayOf(2361569, 7604449, 362609, 1182177, 7473633, 231793),
		DPPS to intArrayOf(1251648, 7543104),
		RSQRTSS to intArrayOf(1182546, 7474002),
		SHA1NEXTE to intArrayOf(1183944, 7475400),
		PUNPCKLBW to intArrayOf(2361440, 7604320, 1182048, 7473504),
		MOVQ2DQ to intArrayOf(2231254),
		PSHUFLW to intArrayOf(1247856, 7539312),
		COMISS to intArrayOf(1181743, 7473199),
		PMOVZXDQ to intArrayOf(1184053, 7475509),
		SQRTSS to intArrayOf(1182545, 7474001),
		MOVNTPD to intArrayOf(69077291),
		CMPUNORDSD to intArrayOf(1182402, 7473858),
		DIVPD to intArrayOf(1182046, 7473502),
		PSUBUSW to intArrayOf(2361561, 7604441, 1182169, 7473625),
		PEXTRQ to intArrayOf(85793046, 85924118),
		PCLMULLQLQDQ to intArrayOf(1186116, 7477572),
		MOVSHDUP to intArrayOf(1182486, 7473942),
		CMPLTSD to intArrayOf(1182402, 7473858),
		PXOR to intArrayOf(2361583, 7604463, 1182191, 7473647),
		CVTSD2SS to intArrayOf(1182298, 7473754),
		PEXTRW to intArrayOf(2820293, 1771973, 68884757, 69146901, 85793045),
		CMPSS to intArrayOf(1248194, 7539650),
		MOVNTPS to intArrayOf(69077035),
		DIVPS to intArrayOf(1181790, 7473246),
		PUNPCKHQDQ to intArrayOf(1182061, 7473517),
		PEXTRB to intArrayOf(68884756, 69146900, 69015828),
		PMOVSXWD to intArrayOf(1184035, 7475491),
		PEXTRD to intArrayOf(68884758, 69146902),
		SHUFPD to intArrayOf(1247686, 7539142),
		MASKMOVQ to intArrayOf(2361591),
		CMPNLTSD to intArrayOf(1182402, 7473858),
		PACKSSDW to intArrayOf(2361451, 7604331, 1182059, 7473515),
		PMOVZXBQ to intArrayOf(1184050, 7475506),
		PMOVZXBW to intArrayOf(1184048, 7475504),
		PMULUDQ to intArrayOf(2361588, 7604468, 1182196, 7473652),
		CMPEQPD to intArrayOf(1182146, 7473602),
		PUNPCKLDQ to intArrayOf(2361442, 7604322, 1182050, 7473506),
		SHUFPS to intArrayOf(1247430, 7538886),
		CMPNEQSS to intArrayOf(1182658, 7474114),
		MINPS to intArrayOf(1181789, 7473245),
		PMOVSXWQ to intArrayOf(1184036, 7475492),
		CMPNLTSS to intArrayOf(1182658, 7474114),
		PSRLDQ to intArrayOf(223603),
		MOVD to intArrayOf(5507182, 7604334, 69863550, 70125694, 69077374, 7473518, 5376366, 7473518, 68815230, 69077374),
		PSUBUSB to intArrayOf(2361560, 7604440, 1182168, 7473624),
		PMOVZXBD to intArrayOf(1184049, 7475505),
		ADDPS to intArrayOf(1181784, 7473240),
		CVTTPS2DQ to intArrayOf(1182555, 7474011),
		MINPD to intArrayOf(1182045, 7473501),
		MOVQ to intArrayOf(2361455, 7604335, 69470335, 70125695, 23332974, 24381550, 86771838, 86902910, 1182590, 68291030, 69077462, 7474046, 23202158, 24250734, 85723518, 85854590),
		CMPEQPS to intArrayOf(1181890, 7473346),
		CMPNEQSD to intArrayOf(1182402, 7473858),
		SHA1RNDS4 to intArrayOf(1251532, 7542988),
	)


}