package pl.touk.nussknacker.engine.api.typed

import cats.data.{Validated, ValidatedNel}
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import io.circe.Json.{fromBoolean, fromDouble, fromFloat, fromInt, fromLong, fromString}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}

object SimpleObjectEncoder {
  private val intClass = Typed.typedClass[Int]
  private val longClass = Typed.typedClass[Long]
  private val floatClass = Typed.typedClass[Float]
  private val doubleClass = Typed.typedClass[Double]
  private val booleanClass = Typed.typedClass[Boolean]
  private val stringClass = Typed.typedClass[String]

  def encode(typ: TypedClass, data: Any): ValidatedNel[String, Json] = (typ, data) match {
    case (`intClass`, intValue: Int) => Validated.validNel(fromInt(intValue))
    case (`longClass`, longValue: Long) => Validated.validNel(fromLong(longValue))
    case (`floatClass`, floatValue: Float) =>
      fromFloat(floatValue).map(Validated.validNel).getOrElse(Validated.invalidNel(s"Could not encode $floatValue as json."))
    case (`doubleClass`, doubleValue: Double) =>
      fromDouble(doubleValue).map(Validated.validNel).getOrElse(Validated.invalidNel(s"Could not encode $doubleValue as json."))
    case (`booleanClass`, booleanValue: Boolean) => Validated.validNel(fromBoolean(booleanValue))
    case (`stringClass`, stringValue: String) => Validated.validNel(fromString(stringValue))
    case _ => Validated.invalidNel(s"No encoding logic for $typ.")
  }

  def decode(typ: TypedClass, obj: ACursor): Decoder.Result[Any] = typ match {
    case `intClass` => obj.as[Int]
    case `longClass` => obj.as[Long]
    case `floatClass` => obj.as[Float]
    case `doubleClass` => obj.as[Double]
    case `booleanClass` => obj.as[Boolean]
    case `stringClass` => obj.as[String]
    case typ => Left(DecodingFailure(s"No decoding logic for $typ.", List()))
  }
}
