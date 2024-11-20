package pl.touk.nussknacker.engine.api.typed

import cats.implicits.toTraverseOps
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import pl.touk.nussknacker.engine.api.json.FromJsonDecoder
import pl.touk.nussknacker.engine.api.typed.typing._

import java.math.BigInteger
import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period}
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
  private val localDateClass     = Typed.typedClass[LocalDate]
  private val localTimeClass     = Typed.typedClass[LocalTime]
  private val durationClass      = Typed.typedClass[Duration]
  private val periodClass        = Typed.typedClass[Period]

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
    case `localDateClass`     => obj.as[String].map(LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE))
    case `localTimeClass`     => obj.as[String].map(LocalTime.parse(_, DateTimeFormatter.ISO_LOCAL_TIME))
    case `durationClass`      => obj.as[String].map(Duration.parse)
    case `periodClass`        => obj.as[String].map(Period.parse)

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
        decodedFields <-
          fieldsJson.toList.traverse { case (fieldName, fieldJson) =>
            val fieldType = record.fields.getOrElse(fieldName, Unknown)
            decodeValue(fieldType, fieldJson.hcursor).map(fieldName -> _)
          }
      } yield decodedFields.toMap.asJava
    case Unknown =>
      /// For Unknown we fallback to generic json to any conversion. It won't work for some types such as LocalDate but for others should work correctly
      obj.as[Json].map(FromJsonDecoder.jsonToAny)
    case typ => Left(DecodingFailure(s"Decoding of type [$typ] is not supported.", List()))
  }

}
