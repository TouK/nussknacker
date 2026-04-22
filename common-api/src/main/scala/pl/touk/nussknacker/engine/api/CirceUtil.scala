package pl.touk.nussknacker.engine.api

import io.circe
import io.circe._
import io.circe.generic.extras.Configuration

import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object CirceUtil {

  implicit val configuration: Configuration = Configuration.default.withDefaults
    .withDiscriminator("type")

  def decodeJson[T: Decoder](json: String): Either[circe.Error, T] =
    io.circe.parser.parse(json).flatMap(Decoder[T].decodeJson)

  def decodeJson[T: Decoder](json: Array[Byte]): Either[circe.Error, T] =
    decodeJson(toString(json))

  def decodeJsonUnsafe[T: Decoder](json: String): T = unsafe(decodeJson(json), json)

  def decodeJsonUnsafe[T: Decoder](json: String, message: String): T = unsafe(decodeJson(json), json, message)

  def decodeJsonUnsafe[T: Decoder](json: Array[Byte]): T = {
    val jsonString = toString(json)
    unsafe(decodeJson(jsonString), jsonString)
  }

  def decodeJsonUnsafe[T: Decoder](json: Array[Byte], message: String): T = {
    val jsonString = toString(json)
    unsafe(decodeJson(jsonString), jsonString, message)
  }

  def decodeJsonUnsafe[T: Decoder](json: Json): T = unsafe(Decoder[T].decodeJson(json), json.asString.getOrElse(""))

  def decodeJsonUnsafe[T: Decoder](json: Json, message: String): T =
    unsafe(Decoder[T].decodeJson(json), json.asString.getOrElse(""), message)

  private def toString(json: Array[Byte]): String =
    new String(json, StandardCharsets.UTF_8)

  private def unsafe[T](result: Either[circe.Error, T], json: => String, message: String = "") = result match {
    case Left(error) =>
      throw DecodingError(
        s"Failed to decode${if (message.isBlank) "" else s" - $message"}, error: ${error.getMessage}, json: $json",
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

    def toMapExcluding(keys: String*): Decoder.Result[Map[String, String]] = {
      val keysSet = keys.toSet
      cursor
        .as[Map[String, String]]
        .map {
          _.filter { case (k, _) => !keysSet.contains(k) }
        }
    }

  }

  // For backward compatibility: wraps a decoder, so that when the "name" field is missing in JSON, "name" is filled from the "id" value,
  // and "id" is replaced with a deterministic UUID generated from that name.
  def withNameFromIdFallback[T](decoder: Decoder[T]): Decoder[T] =
    Decoder.instance(c => decoder.decodeJson(fillNameAndGenerateIdIfMissing(c.value)))

  private def fillNameAndGenerateIdIfMissing(json: Json): Json =
    json.mapObject { obj =>
      if (obj.contains("name")) obj
      else {
        obj("id")
          .flatMap(_.asString)
          .fold(obj) { id =>
            val uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString
            obj.add("name", Json.fromString(id)).add("id", Json.fromString(uuid))
          }
      }
    }

  // Be default circe print all empty values as a nulls which can be good for programs because it is more explicit
  // that some value is null, but for people it generates a lot of unnecessary noise
  val humanReadablePrinter: Printer = Printer.spaces2.copy(dropNullValues = true)

  // Helpers for a convention where a case class exposes `unsafeFields: Map[String, Json]` that is
  // encoded/decoded as top-level JSON properties prefixed with `_unsafe_` (rather than under a
  // nested field). Used to round-trip forward-compatible extension fields without schema changes.
  object UnsafePrefixedFields {
    val Prefix = "_unsafe_"

    def extract(c: HCursor): Map[String, Json] =
      c.keys.toList.flatten
        .filter(_.startsWith(Prefix))
        .flatMap(k => c.downField(k).as[Json].toOption.map(k -> _))
        .toMap

    // Only entries whose key already starts with [[Prefix]] are merged in — keeps a stray entry in
    // the map (e.g. `"description" -> ...`) from clobbering a real top-level field during encode.
    def merge(base: JsonObject, unsafeFields: Map[String, Json]): JsonObject =
      unsafeFields.foldLeft(base) {
        case (obj, (k, v)) if k.startsWith(Prefix) => obj.add(k, v)
        case (obj, _)                              => obj
      }

    // Recursively removes every object key starting with [[Prefix]] from the JSON tree — used to
    // keep the extension data local (e.g. scrubbed before the scenario is handed off to Flink).
    def deepStrip(json: Json): Json =
      json.arrayOrObject(
        json,
        arr => Json.fromValues(arr.map(deepStrip)),
        obj =>
          Json.fromJsonObject(
            JsonObject.fromIterable(
              obj.toIterable
                .filterNot { case (k, _) => k.startsWith(Prefix) }
                .map { case (k, v) => (k, deepStrip(v)) }
            )
          )
      )

  }

}
