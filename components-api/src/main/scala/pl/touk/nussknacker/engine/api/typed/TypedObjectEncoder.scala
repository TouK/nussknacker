package pl.touk.nussknacker.engine.api.typed

import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json}
import io.circe.Json.{fromBoolean, fromDouble, fromFloat, fromInt, fromLong, fromString}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}

object TypedObjectEncoder {
  private val intClass = Typed.typedClass[Int]
  private val longClass = Typed.typedClass[Long]
  private val floatClass = Typed.typedClass[Float]
  private val doubleClass = Typed.typedClass[Double]
  private val booleanClass = Typed.typedClass[Boolean]
  private val stringClass = Typed.typedClass[String]

  def encode(typ: TypedClass, data: Any): Json = (typ, data) match {
    case (`intClass`, intValue: Int) => fromInt(intValue)
    case (`longClass`, longValue: Long) => fromLong(longValue)
    case (`floatClass`, floatValue: Float) => fromFloat(floatValue).get // FIXME: Error handling.
    case (`doubleClass`, doubleValue: Double) => fromDouble(doubleValue).get
    case (`booleanClass`, booleanValue: Boolean) => fromBoolean(booleanValue)
    case (`stringClass`, stringValue: String) => fromString(stringValue)
    case _ => Json.Null
    // TODO: Handle illegal cases.
  }

  def decode(typ: TypedClass, obj: ACursor): Decoder.Result[Any] = typ match {
    case `intClass` => obj.as[Int]
    case `longClass` => obj.as[Long]
    case `floatClass` => obj.as[Float]
    case `doubleClass` => obj.as[Double]
    case `booleanClass` => obj.as[Boolean]
    case `stringClass` => obj.as[String]
    case typ => Left(DecodingFailure(s"No deserialization logic for $typ.", List()))
  }
}
