package app.trazo.gpx.osm

class JsonException(message: String) : Exception(message)

/**
 * Lector JSON mínimo y sin dependencias (el core es Kotlin puro).
 * Devuelve Map<String, Any?>, List<Any?>, String, Double, Boolean o null.
 */
object Json {

    fun parse(s: String): Any? {
        val p = Reader(s)
        val v = p.value()
        p.ws()
        if (p.i != s.length) throw JsonException("Texto de más en la posición ${p.i}")
        return v
    }

    private class Reader(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            ws()
            if (i >= s.length) throw JsonException("JSON incompleto")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (s.getOrNull(i) == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                expect(':')
                m[k] = value()
                ws()
                if (s.getOrNull(i) == ',') { i++; continue }
                expect('}')
                return m
            }
        }

        private fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (s.getOrNull(i) == ']') { i++; return l }
            while (true) {
                l.add(value())
                ws()
                if (s.getOrNull(i) == ',') { i++; continue }
                expect(']')
                return l
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonException("Cadena sin cerrar")
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s.getOrNull(i++) ?: throw JsonException("Escape incompleto")) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = s.substring(i, (i + 4).coerceAtMost(s.length))
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonException("\\u no válido"))
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            return s.substring(start, i).toDoubleOrNull() ?: throw JsonException("Valor no válido en la posición $start")
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("Valor no válido en la posición $i")
            i += word.length
            return v
        }

        private fun expect(c: Char) {
            if (s.getOrNull(i) != c) throw JsonException("Se esperaba '$c' en la posición $i")
            i++
        }
    }
}
