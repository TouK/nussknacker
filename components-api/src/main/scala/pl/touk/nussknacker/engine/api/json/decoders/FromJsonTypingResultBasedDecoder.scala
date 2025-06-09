package pl.touk.nussknacker.engine.api.json.decoders

import cats.implicits.toTraverseOps
import io.circe._
import org.apache.commons.lang3.LocaleUtils
import org.springframework.util.StringUtils
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import pl.touk.nussknacker.engine.api.typed.typing._

import java.math.BigInteger
import java.nio.charset.Charset
import java.time._
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._

object FromJsonTypingResultBasedDecoder extends LazyLogging {
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

  // date-time types
  private val instantClass        = Typed.typedClass[Instant]
  private val offsetDateTimeClass = Typed.typedClass[OffsetDateTime]
  private val zonedDateTimeClass  = Typed.typedClass[ZonedDateTime]
  private val localDateTimeClass  = Typed.typedClass[LocalDateTime]
  private val localDateClass      = Typed.typedClass[LocalDate]
  private val localTimeClass      = Typed.typedClass[LocalTime]
  private val durationClass       = Typed.typedClass[Duration]
  private val periodClass         = Typed.typedClass[Period]
  private val zoneOffsetClass     = Typed.typedClass[ZoneOffset]
  private val zoneIdClass         = Typed.typedClass[ZoneId]

  // other logical types with well-known string representation
  private val currencyClass = Typed.typedClass[Currency]
  private val charsetClass  = Typed.typedClass[Charset]
  private val localeClass   = Typed.typedClass[Locale]
  private val uuidClass     = Typed.typedClass[UUID]

  def decodeValue(typ: TypingResult, obj: ACursor): Decoder.Result[Any] = typ match {
    case _ if obj.isInstanceOf[HCursor] && obj.asInstanceOf[HCursor].value == Json.Null => Right(null)
    case TypedNull                                                                      => Right(null)
    case TypedObjectWithValue(_, value)                                                 => Right(value)
    case `intClass`                                                                     => obj.as[Int]
    case `shortClass`                                                                   => obj.as[Short]
    case `longClass`                                                                    => obj.as[Long]
    case `floatClass`                                                                   => obj.as[Float]
    case `doubleClass`                                                                  => obj.as[Double]
    case `booleanClass`                                                                 => obj.as[Boolean]
    case `stringClass`                                                                  => obj.as[String]
    case `byteClass`                                                                    => obj.as[Byte]
    case `bigIntegerClass`                                                              => obj.as[BigInteger]
    case `bigDecimalClass`                                                              => obj.as[java.math.BigDecimal]

    // date-time types
    case `instantClass`        => obj.as[String].map(Instant.parse)
    case `offsetDateTimeClass` => obj.as[String].map(OffsetDateTime.parse)
    case `zonedDateTimeClass`  => obj.as[String].map(ZonedDateTime.parse)
    case `localDateTimeClass`  => obj.as[String].map(LocalDateTime.parse)
    case `localDateClass`      => obj.as[String].map(LocalDate.parse)
    case `localTimeClass`      => obj.as[String].map(LocalTime.parse)
    case `durationClass`       => obj.as[String].map(Duration.parse)
    case `periodClass`         => obj.as[String].map(Period.parse)
    case `zoneOffsetClass`     => obj.as[String].map(ZoneOffset.of)
    case `zoneIdClass`         => obj.as[String].map(ZoneId.of)

    // other logical types with well-known string representation
    case `currencyClass` => obj.as[String].map(Currency.getInstance)
    case `charsetClass`  => obj.as[String].map(Charset.forName)
    case `localeClass` =>
      obj.as[String].map { source =>
        val locale = StringUtils.parseLocale(source)
        assert(LocaleUtils.isAvailableLocale(locale)) // without this check even "qwerty" is considered a Locale
        locale
      }
    case `uuidClass` =>
      obj.as[String].map { source =>
        if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null
      }

    case TypedClass(klass, List(elementType: TypingResult)) if klass == classOf[java.util.List[_]] =>
      obj.values match {
        case Some(values) =>
          values.toList
            .traverse(v => decodeValue(elementType, v.hcursor))
            .map(_.asJava)
        case None =>
          Left(DecodingFailure(s"Expected encoded List to be a Json array", List()))
      }
    case TypedClass(klass, List(elementType: TypingResult)) if klass == Typed.KlassForArrays =>
      obj.values match {
        case Some(values) =>
          values.toList
            .traverse(v => decodeValue(elementType, v.hcursor))
            .map(convertToArray(_, elementType))
        case None =>
          Left(DecodingFailure(s"Expected encoded Array to be a Json array", List()))
      }
    case record: TypedObjectTypingResult =>
      for {
        fieldsJson <- obj.as[Map[String, Json]]
        decodedFields <-
          fieldsJson.toList.traverse { case (fieldName, fieldJson) =>
            val fieldType = record.fields.getOrElse(fieldName, Unknown)
            decodeValue(fieldType, fieldJson.hcursor).map(fieldName -> _)
          }
        // We don't check runtimeObjType and assume that the runtime type is java Map which is wrong in some cases (Avro, table-api) and might cause Flink serialization problems
        // TODO: We should either accept any supported record type during serialization/deserialization in every place or respect runtimeObjType
        javaMap = decodedFields.toMap.asJava
      } yield javaMap
    case unknown @ Unknown(_) =>
      /// For Unknown we fallback to generic json to any conversion. It won't work for some types such as LocalDate but for others should work correctly
      obj.as[Json].map { json =>
        val result = FromJsonSimpleDecoder.jsonToAny(json)
        logger.debug(
          s"Target type for json [${json.noSpaces}] decoding is [$unknown] type. For decoding was used simple decoder. Result is [$result]"
        )
        result
      }
    case typ => Left(DecodingFailure(s"Decoding of type [$typ] is not supported.", List()))
  }

  private def convertToArray(list: List[Any], elementType: TypingResult) = elementType match {
    case single: SingleTypingResult =>
      val reflectiveCreatedArray =
        java.lang.reflect.Array.newInstance(single.runtimeObjType.klass, list.size).asInstanceOf[Array[Any]]
      list.copyToArray(
        reflectiveCreatedArray
      ) // Idea marks this line as unused code, but it is not true - it produces a side effect that is important for us
      reflectiveCreatedArray
    case _ =>
      list.toArray[Any]
  }

}
