package app.trazo.gpx.i18n

import java.util.Locale

/** Idiomas de Trazo. */
enum class Lang(val tag: String) {
    ES("es-ES"),
    EN("en-GB");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** El idioma del sistema: español si lo es; si no, inglés. */
        fun of(locale: Locale): Lang = if (locale.language == "es") ES else EN
    }
}

/**
 * El idioma con el que habla Trazo. Lo fija la app al arrancar (el del
 * sistema o el elegido en Info); los textos van en pares junto a donde se usan,
 * como en Trazo web: `L("Inicio", "Home")`.
 */
object I18n {
    @Volatile
    var lang: Lang = Lang.ES

    /** Locale para números y fechas. */
    val locale: Locale get() = lang.locale
}

/** El texto en el idioma actual. */
fun L(es: String, en: String): String = if (I18n.lang == Lang.ES) es else en

/** "15 %" en español, "15%" en inglés. */
fun percent(n: Any): String = if (I18n.lang == Lang.ES) "$n %" else "$n%"
