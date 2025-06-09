package pl.touk.nussknacker.engine.api.json.decoders

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import org.apache.commons.lang3.LocaleUtils
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.typing._

import java.math.BigInteger
import java.nio.charset.Charset
import java.time._
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

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

  def decodeValue(typ: TypingResult, cursor: ACursor): Decoder.Result[Any] = {
    def handleExceptionAsDecodingFailureF[I, O] =
      handleExceptionAsDecodingFailure[I, O](cursor) _
    typ match {
      case _ if cursor.isInstanceOf[HCursor] && cursor.asInstanceOf[HCursor].value == Json.Null => Right(null)
      case TypedNull                                                                            => Right(null)
      case TypedObjectWithValue(_, value)                                                       => Right(value)
      case `intClass`                                                                           => cursor.as[Int]
      case `shortClass`                                                                         => cursor.as[Short]
      case `longClass`                                                                          => cursor.as[Long]
      case `floatClass`                                                                         => cursor.as[Float]
      case `doubleClass`                                                                        => cursor.as[Double]
      case `booleanClass`                                                                       => cursor.as[Boolean]
      case `stringClass`                                                                        => cursor.as[String]
      case `byteClass`                                                                          => cursor.as[Byte]
      case `bigIntegerClass`                                                                    => cursor.as[BigInteger]
      case `bigDecimalClass` => cursor.as[java.math.BigDecimal]

      // date-time types
      case `instantClass`        => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(Instant.parse))
      case `offsetDateTimeClass` => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(OffsetDateTime.parse))
      case `zonedDateTimeClass`  => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(ZonedDateTime.parse))
      case `localDateTimeClass`  => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(LocalDateTime.parse))
      case `localDateClass`      => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(LocalDate.parse))
      case `localTimeClass`      => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(LocalTime.parse))
      case `durationClass`       => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(Duration.parse))
      case `periodClass`         => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(Period.parse))
      case `zoneOffsetClass`     => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(ZoneOffset.of))
      case `zoneIdClass`         => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(ZoneId.of))

      // other logical types with well-known string representation
      case `currencyClass` => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(Currency.getInstance))
      case `charsetClass`  => cursor.as[String].flatMap(handleExceptionAsDecodingFailureF(Charset.forName))
      case `localeClass` =>
        for {
          localeString   <- cursor.as[String]
          locale         <- handleExceptionAsDecodingFailureF(StringUtils.parseLocale)(localeString)
          verifiedLocale <-
            // without this check, even "qwerty" is considered a Locale
            if (LocaleUtils.isAvailableLocale(locale)) {
              Right(locale)
            } else {
              Left(DecodingFailure(s"Not supported Locale: $localeString", cursor.history))
            }
        } yield verifiedLocale
      case `uuidClass` =>
        cursor.as[String].flatMap { source =>
          if (StringUtils.hasLength(source)) handleExceptionAsDecodingFailureF(UUID.fromString)(source.trim) else null
        }

      case TypedClass(klass, List(elementType: TypingResult)) if klass == classOf[java.util.List[_]] =>
        cursor.values match {
          case Some(values) =>
            values.toList
              .traverse(v => decodeValue(elementType, v.hcursor))
              .map(_.asJava)
          case None =>
            Left(DecodingFailure(s"Expected encoded List to be a Json array", cursor.history))
        }
      case TypedClass(klass, List(elementType: TypingResult)) if klass == Typed.KlassForArrays =>
        cursor.values match {
          case Some(values) =>
            values.toList
              .traverse(v => decodeValue(elementType, v.hcursor))
              .map(convertToArray(_, elementType))
          case None =>
            Left(DecodingFailure(s"Expected encoded Array to be a Json array", cursor.history))
        }
      case record: TypedObjectTypingResult =>
        for {
          fieldsJson <- cursor.as[Map[String, Json]]
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
        cursor.as[Json].map { json =>
          val result = FromJsonSimpleDecoder.jsonToAny(json)
          logger.debug(
            s"Target type for json [${json.noSpaces}] decoding is [$unknown] type. For decoding was used simple decoder. Result is [$result]"
          )
          result
        }
      case typ => Left(DecodingFailure(s"Decoding of type [$typ] is not supported.", cursor.history))
    }
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

  private def handleExceptionAsDecodingFailure[I, O](cursor: ACursor)(f: I => O): I => Decoder.Result[O] =
    (input: I) => {
      try {
        Right(f(input))
      } catch {
        case NonFatal(ex) =>
          Left(DecodingFailure(ex.getMessage, cursor.history))
      }
    }

}
