package pl.touk.nussknacker.engine.api.typed

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import io.circe.Json.{fromBigDecimal, fromBigInt, fromBoolean, fromDouble, fromFloat, fromInt, fromLong, fromString}
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import pl.touk.nussknacker.engine.api.typed.typing._

import java.math.BigInteger
import scala.jdk.CollectionConverters._

// TODO: Add support for more types.
object SimpleObjectEncoder {
  private val intClass        = Typed.typedClass[Int]
  private val shortClass      = Typed.typedClass[Short]
  private val longClass       = Typed.typedClass[Long]
  private val floatClass      = Typed.typedClass[Float]
  private val doubleClass     = Typed.typedClass[Double]
  private val booleanClass    = Typed.typedClass[Boolean]
  private val stringClass     = Typed.typedClass[String]
  private val byteClass       = Typed.typedClass[Byte]
  private val bigIntegerClass = Typed.typedClass[BigInteger]
  private val bigDecimalClass = Typed.typedClass[java.math.BigDecimal]

  def encodeValue(value: Any): ValidatedNel[String, Json] = value match {
    case null                        => Json.Null.validNel
    case value: Int                  => fromInt(value).validNel
    case value: Short                => fromInt(value).validNel
    case value: Long                 => fromLong(value).validNel
    case value: Boolean              => fromBoolean(value).validNel
    case value: String               => fromString(value).validNel
    case value: Byte                 => fromInt(value).validNel
    case value: BigInteger           => fromBigInt(value).validNel
    case value: java.math.BigDecimal => fromBigDecimal(value).validNel
    case value: Float =>
      fromFloat(value).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)
    case value: Double =>
      fromDouble(value).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)

    case vals: java.util.Collection[_] =>
      val encodedValues = vals.asScala.map(elem => encodeValue(elem)).toList.sequence
      encodedValues.map(values => Json.fromValues(values))
    case vals: java.util.Map[_, _] =>
      val encodedMap: Validated[NonEmptyList[String], List[(String, Json)]] = vals.asScala
        .map { case (key, value) =>
          encodeValue(key).andThen(encodedKey =>
            encodedKey.asString match {
              case Some(encodedKeyString) => encodeValue(value).map(encodedValue => encodedKeyString -> encodedValue)
              case None                   => s"Failed to encode Record key '$encodedKey' as String".invalidNel
            }
          )
        }
        .toList
        .sequence

      encodedMap.map(values => Json.fromFields(values))
    case value =>
      s"No encoding logic for value $value of class ${value.getClass}".invalidNel
  }

  def decodeValue(typ: TypingResult, obj: ACursor): Decoder.Result[Any] = typ match {
    case TypedObjectWithValue(_, value) => Right(value)
    case TypedNull                      => Right(null)
    case `intClass`                     => obj.as[Int]
    case `shortClass`                   => obj.as[Short]
    case `longClass`                    => obj.as[Long]
    case `floatClass`                   => obj.as[Float]
    case `doubleClass`                  => obj.as[Double]
    case `booleanClass`                 => obj.as[Boolean]
    case `stringClass`                  => obj.as[String]
    case `byteClass`                    => obj.as[Byte]
    case `bigIntegerClass`              => obj.as[BigInteger]
    case `bigDecimalClass`              => obj.as[BigDecimal]
    case TypedClass(klass, List(elementType: TypingResult)) if klass == classOf[java.util.List[_]] =>
      obj.values
        .getOrElse {
          throw new RuntimeException(s"$typ  -  ${obj.toString}")
        }
        .toList
        .traverse(v => decodeValue(elementType, v.hcursor))
        .map(_.asJava)
    case record: TypedObjectTypingResult =>
      for {
        fields <- obj.as[Map[String, Json]]
        decodedFields <- fields.toList.traverse { case (fieldName, fieldJson) =>
          record.fields.get(fieldName) match {
            case Some(fieldTyp) => decodeValue(fieldTyp, fieldJson.hcursor).map(fieldName -> _)
            case None =>
              Left(
                DecodingFailure(
                  s"Record field '$fieldName' isn't present in known Record fields: ${record.fields}",
                  List()
                )
              )
          }
        }
      } yield decodedFields.toMap.asJava
    case typ => Left(DecodingFailure(s"No decoding logic for $typ.", List()))
  }

}
