package pl.touk.nussknacker.engine.api.typed

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import io.circe.Json.{fromBoolean, fromDouble, fromFloat, fromInt, fromLong, fromString}
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass

object TypedObjectEncoder {
  private val intClass = classOf[Int]
  private val longClass = classOf[Double]
  private val floatClass = classOf[Float]
  private val doubleClass = classOf[Double]
  private val booleanClass = classOf[Boolean]
  private val stringClass = classOf[String]

  def encode(typ: TypedClass, data: Any): Json = (typ, data) match {
    case (TypedClass(`intClass`, Nil), intValue: Int) => fromInt(intValue)
    case (TypedClass(`longClass`, Nil), longValue: Long) => fromLong(longValue)
    case (TypedClass(`floatClass`, Nil), floatValue: Float) => fromFloat(floatValue).get // FIXME: Error handling.
    case (TypedClass(`doubleClass`, Nil), doubleValue: Double) => fromDouble(doubleValue).get
    case (TypedClass(`booleanClass`, Nil), booleanValue: Boolean) => fromBoolean(booleanValue)
    case (TypedClass(`stringClass`, Nil), stringValue: String) => fromString(stringValue)
    case _ => Json.Null
    // TODO: Handle illegal cases.
  }

  def decode(typ: TypedClass, obj: HCursor): Decoder.Result[Any] = typ match {
    case TypedClass(`intClass`, Nil) => obj.as[Int]
    case TypedClass(`longClass`, Nil) => obj.as[Long]
    case TypedClass(`floatClass`, Nil) => obj.as[Float]
    case TypedClass(`doubleClass`, Nil) => obj.as[Double]
    case TypedClass(`booleanClass`, Nil) => obj.as[Boolean]
    case TypedClass(`stringClass`, Nil) => obj.as[String]
    case typ => Left(DecodingFailure(s"No deserialization logic for $typ.", List()))
  }
}
