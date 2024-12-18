package pl.touk.nussknacker.engine.util

import java.io.File
import java.net.{URI, URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.util.Try

/**
  * Utility class for JavaScript compatible UTF-8 encoding and decoding.
  *
  * @see http://stackoverflow.com/questions/607176/java-equivalent-to-javascripts-encodeuricomponent-that-produces-identical-output
  */
object UriUtils {

  def extractListOfLocations(commaSeparatedListOfLocations: String): List[URI] = {
    for {
      singleElement <- commaSeparatedListOfLocations.split(",").toList
      trimmed = singleElement.trim
    } yield Try(URI.create(trimmed)).filter(_.getScheme.nonEmpty).getOrElse(new File(trimmed).toURI)
  }

  /**
    * Decodes the passed UTF-8 String using an algorithm that's compatible with
    * JavaScript's <code>decodeURIComponent</code> function. Returns
    * <code>null</code> if the String is <code>null</code>.
    */
  def decodeURIComponent(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  /**
    * Encodes the passed String as UTF-8 using an algorithm that's compatible
    * with JavaScript's <code>encodeURIComponent</code> function. Returns
    * <code>null</code> if the String is <code>null</code>.
    */
  def encodeURIComponent(value: String): String =
    URLEncoder
      .encode(value, StandardCharsets.UTF_8)
      .replaceAll("\\+", "%20")
      .replaceAll("\\!", "%21")
      .replaceAll("\\'", "%27")
      .replaceAll("\\(", "%28")
      .replaceAll("\\)", "%29")
      .replaceAll("\\~", "%7E")

}
