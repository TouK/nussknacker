package pl.touk.nussknacker.engine.api.typed

import cats.implicits.toTraverseOps
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import pl.touk.nussknacker.engine.api.typed.typing._

import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

object ValueDecoder {
  private val intClass           = Typed.typedClass[Int]
  private val shortClass         = Typed.typedClass[Short]
  private val longClass          = Typed.typedClass[Long]
  private val floatClass         = Typed.typedClass[Float]
  private val doubleClass        = Typed.typedClass[Double]
  private val booleanClass       = Typed.typedClass[Boolean]
  private val stringClass        = Typed.typedClass[String]
  private val byteClass          = Typed.typedClass[Byte]
  private val bigIntegerClass    = Typed.typedClass[BigInteger]
  private val bigDecimalClass    = Typed.typedClass[java.math.BigDecimal]
  private val localDateTimeClass = Typed.typedClass[LocalDateTime]

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
    case `bigDecimalClass`              => obj.as[java.math.BigDecimal]
    case `localDateTimeClass` => obj.as[String].map(LocalDateTime.parse(_, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    case TypedClass(klass, List(elementType: TypingResult)) if klass == classOf[java.util.List[_]] =>
      obj.values match {
        case Some(values) =>
          values.toList
            .traverse(v => decodeValue(elementType, v.hcursor))
            .map(_.asJava)
        case None =>
          Left(DecodingFailure(s"Expected encoded List to be a Json array", List()))
      }
    case record: TypedObjectTypingResult =>
      for {
        fieldsJson <- obj.as[Map[String, Json]]
        decodedFields <- record.fields.toList.traverse { case (fieldName, fieldType) =>
          fieldsJson.get(fieldName) match {
            case Some(fieldJson) => decodeValue(fieldType, fieldJson.hcursor).map(fieldName -> _)
            case None =>
              Left(
                DecodingFailure(
                  s"Record field '$fieldName' isn't present in encoded Record fields: $fieldsJson",
                  List()
                )
              )
          }
        }
      } yield decodedFields.toMap.asJava
    case typ => Left(DecodingFailure(s"Decoding of type [$typ] is not supported.", List()))
  }

}
