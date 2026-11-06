package app.trazo.gpx

internal sealed interface XmlEvent {
    data class Start(val name: String, val attrs: Map<String, String>) : XmlEvent
    data class End(val name: String) : XmlEvent
    data class Text(val text: String) : XmlEvent
}

/**
 * Lector XML mínimo, suficiente para GPX y sin dependencias, para que el
 * mismo código funcione igual en Android y en los tests de JVM.
 * Ignora prólogo, comentarios y DOCTYPE; quita prefijos de namespace;
 * decodifica entidades y CDATA. Una etiqueta `<a/>` emite Start + End.
 */
internal class XmlReader(private val src: String) {
    private var i = 0

    fun events(): Sequence<XmlEvent> = sequence {
        while (i < src.length) {
            if (src[i] != '<') {
                val end = src.indexOf('<', i).let { if (it < 0) src.length else it }
                val raw = src.substring(i, end)
                if (raw.isNotBlank()) yield(XmlEvent.Text(decode(raw)))
                i = end
                continue
            }
            when {
                src.startsWith("<!--", i) -> skipPast("-->")
                src.startsWith("<![CDATA[", i) -> {
                    val end = src.indexOf("]]>", i + 9)
                    if (end < 0) fail("CDATA sin cerrar")
                    yield(XmlEvent.Text(src.substring(i + 9, end)))
                    i = end + 3
                }
                src.startsWith("<?", i) -> skipPast("?>")
                src.startsWith("<!", i) -> skipPast(">")
                src.startsWith("</", i) -> {
                    val end = src.indexOf('>', i)
                    if (end < 0) fail("Etiqueta de cierre sin terminar")
                    yield(XmlEvent.End(localName(src.substring(i + 2, end).trim())))
                    i = end + 1
                }
                else -> {
                    val (start, selfClosing) = readStart()
                    yield(start)
                    if (selfClosing) yield(XmlEvent.End(start.name))
                }
            }
        }
    }

    private fun readStart(): Pair<XmlEvent.Start, Boolean> {
        i++ // '<'
        val nameStart = i
        while (i < src.length && !src[i].isWhitespace() && src[i] != '>' && src[i] != '/') i++
        if (i == nameStart) fail("Etiqueta sin nombre")
        val name = localName(src.substring(nameStart, i))
        val attrs = mutableMapOf<String, String>()
        while (true) {
            skipWhitespace()
            if (i >= src.length) fail("Etiqueta <$name> sin cerrar")
            when {
                src.startsWith("/>", i) -> { i += 2; return XmlEvent.Start(name, attrs) to true }
                src[i] == '>' -> { i++; return XmlEvent.Start(name, attrs) to false }
            }
            val attrStart = i
            while (i < src.length && src[i] != '=' && !src[i].isWhitespace() && src[i] != '>') i++
            val attrName = localName(src.substring(attrStart, i))
            skipWhitespace()
            if (i >= src.length || src[i] != '=') fail("Atributo '$attrName' sin valor en <$name>")
            i++
            skipWhitespace()
            val quote = src.getOrNull(i)
            if (quote != '"' && quote != '\'') fail("Valor sin comillas en <$name>")
            val valueEnd = src.indexOf(quote, i + 1)
            if (valueEnd < 0) fail("Valor sin cerrar en <$name>")
            attrs[attrName] = decode(src.substring(i + 1, valueEnd))
            i = valueEnd + 1
        }
    }

    private fun skipWhitespace() {
        while (i < src.length && src[i].isWhitespace()) i++
    }

    private fun skipPast(token: String) {
        val end = src.indexOf(token, i)
        if (end < 0) fail("Falta '$token'")
        i = end + token.length
    }

    private fun localName(qName: String) = qName.substringAfter(':')

    private fun fail(msg: String): Nothing = throw GpxParseException(msg)

    companion object {
        private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

        fun decode(s: String): String {
            if ('&' !in s) return s
            return ENTITY.replace(s) { m ->
                when (val e = m.groupValues[1]) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    else -> {
                        val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                        code?.let { String(Character.toChars(it)) } ?: m.value
                    }
                }
            }
        }
    }
}
