package pl.touk.nussknacker.engine.api

import com.github.ghik.silencer.silent
import io.circe
import io.circe._
import io.circe.generic.extras.Configuration

import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object CirceUtil {

  implicit val configuration: Configuration = Configuration.default.withDefaults
    .withDiscriminator("type")

  def decodeJson[T: Decoder](json: String): Either[circe.Error, T] =
    io.circe.parser.parse(json).flatMap(Decoder[T].decodeJson)

  def decodeJson[T: Decoder](json: Array[Byte]): Either[circe.Error, T] =
    decodeJson(new String(json, StandardCharsets.UTF_8))

  def decodeJsonUnsafe[T: Decoder](json: String): T = unsafe(decodeJson(json))

  def decodeJsonUnsafe[T: Decoder](json: String, message: String): T = unsafe(decodeJson(json), message)

  def decodeJsonUnsafe[T: Decoder](json: Array[Byte]): T = unsafe(decodeJson(json))

  def decodeJsonUnsafe[T: Decoder](json: Array[Byte], message: String): T = unsafe(decodeJson(json), message)

  def decodeJsonUnsafe[T: Decoder](json: Json): T = unsafe(Decoder[T].decodeJson(json))

  def decodeJsonUnsafe[T: Decoder](json: Json, message: String): T = unsafe(Decoder[T].decodeJson(json), message)

  private def unsafe[T](result: Either[circe.Error, T], message: String = "") = result match {
    case Left(error) =>
      throw DecodingError(
        s"Failed to decode${if (message.isBlank) "" else s" - $message"}, error: ${error.getMessage}",
        error
      )
    case Right(data) => data
  }

  final case class DecodingError(message: String, ex: Throwable) extends IllegalArgumentException(message, ex)

  object codecs {

    implicit def jMapEncoder[K: KeyEncoder, V: Encoder]: Encoder[java.util.Map[K, V]] =
      Encoder[Map[K, V]].contramap(_.asScala.toMap)

    implicit lazy val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)
    implicit lazy val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)

    implicit val urlEncoder: Encoder[URL] = Encoder.encodeString.contramap(_.toExternalForm)
    implicit val urlDecoder: Decoder[URL] = Decoder.decodeString.map(new URL(_))
  }

  implicit class RichACursor(val cursor: ACursor) extends AnyVal {

    def downAt(p: Json => Boolean): ACursor = {
      @tailrec
      def go(c: ACursor): ACursor = c match {
        case success: HCursor => if (p(success.value)) success else go(success.right)
        case other            => other
      }

      go(cursor.downArray)
    }

  }

  implicit class HCursorExt(val cursor: HCursor) extends AnyVal {

    @silent("deprecated")
    def toMapExcluding(keys: String*): Decoder.Result[Map[String, String]] = {
      val keysSet = keys.toSet
      cursor
        .as[Map[String, String]]
        .map {
          _.filterKeys(k => !keysSet.contains(k)).toMap
        }
    }

  }

  // Be default circe print all empty values as a nulls which can be good for programs because it is more explicit
  // that some value is null, but for people it generates a lot of unnecessary noice
  val humanReadablePrinter: Printer = Printer.spaces2.copy(dropNullValues = true)

}
