package pl.touk.esp.engine.util

import java.time.LocalDateTime

import argonaut.Json.jString
import argonaut._

import scala.util.{Failure, Success, Try}

object Codecs {

  def enumCodec[T <: Enumeration](enumCompanion: T) : CodecJson[enumCompanion.Value] = CodecJson[enumCompanion.Value](
    value => jString(value.toString),
    cursor => cursor.as[String].flatMap { value =>
      Try(enumCompanion.withName(value)) match {
        case Success(processType) => DecodeResult.ok(processType)
        case Failure(_) => DecodeResult.fail(s"$value cannot be converted to " +
          s"${ReflectUtils.fixedClassSimpleNameWithoutParentModule(enumCompanion.getClass)}", CursorHistory(List()))
      }
    }
  )

  implicit def localDateTimeEncode : EncodeJson[LocalDateTime] = EncodeJson.of[String].contramap[LocalDateTime](_.toString)

  implicit def localDateTimeDecode : DecodeJson[LocalDateTime] = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))

}
