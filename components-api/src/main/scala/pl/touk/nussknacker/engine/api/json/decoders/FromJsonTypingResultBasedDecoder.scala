package pl.touk.nussknacker.engine.api.json.decoders

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import org.apache.commons.lang3.LocaleUtils
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils

import java.math.BigInteger
import java.nio.charset.Charset
import java.time._
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import StandardTypesClasses._

object FromJsonTypingResultBasedDecoder extends FromJsonTypingResultBasedDecoder

class FromJsonTypingResultBasedDecoder extends LazyLogging {
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

  def decodeValue(typ: TypingResult, cursor: HCursor): Decoder.Result[Any] = {
    def handleExceptionAsDecodingFailureF[I, O] =
      handleExceptionAsDecodingFailure[I, O](cursor) _
    typ match {
      case _ if cursor.value == Json.Null => Right(null)
      case TypedNull                      => Right(null)
      case TypedObjectWithValue(_, value) => Right(value)
      case `intClass`                     => cursor.as[Int]
      case `shortClass`                   => cursor.as[Short]
      case `longClass`                    => cursor.as[Long]
      case `floatClass`                   => cursor.as[Float]
      case `doubleClass`                  => cursor.as[Double]
      case `booleanClass`                 => cursor.as[Boolean]
      case `stringClass`                  => cursor.as[String]
      case `byteClass`                    => cursor.as[Byte]
      case `bigIntegerClass`              => cursor.as[BigInteger]
      case `bigDecimalClass`              => cursor.as[java.math.BigDecimal]

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
              handleDecodingError(s"Not supported Locale: $localeString", cursor)
            }
        } yield verifiedLocale
      case `uuidClass` =>
        cursor.as[String].flatMap { source =>
          if (StringUtils.hasLength(source))
            handleExceptionAsDecodingFailureF(UUID.fromString)(source.trim)
          else
            Right(null)
        }

      case TypedClass(klass, _) if klass.isEnum =>
        cursor
          .as[String]
          .flatMap(
            handleExceptionAsDecodingFailureF(ReflectUtils.javaEnumValueOf(klass.asInstanceOf[Class[Enum[_]]], _))
          )
      case TypedClass(`ListClass`, elementType :: Nil) =>
        cursor.values match {
          case Some(values) =>
            values.toList
              .traverse(v => decodeValue(elementType, v.hcursor))
              .map(_.asJava)
          case None =>
            handleDecodingError(s"Expected encoded List to be a Json array", cursor)
        }
      case TypedClass(`ArrayClass`, elementType :: Nil) =>
        cursor.values match {
          case Some(values) =>
            values.toList
              .traverse(v => decodeValue(elementType, v.hcursor))
              .map(convertToArray(_, elementType))
          case None =>
            handleDecodingError(s"Expected encoded Array to be a Json array", cursor)
        }
      case TypedClass(`MapClass`, keyType :: valueType :: Nil) =>
        for {
          mapOfJsons <- cursor.as[Map[String, Json]]
          listOfDecodedKeyAndValues <-
            mapOfJsons.toList.map { case (key, value) =>
              for {
                decodedKey   <- decodeValue(keyType, Json.fromString(key).hcursor)
                decodedValue <- decodeValue(valueType, value.hcursor)
              } yield decodedKey -> decodedValue
            }.sequence
        } yield listOfDecodedKeyAndValues.toMap.asJava
      case TypedObjectTypingResult(fields, runtimeObjType, _) if runtimeObjType.klass == MapClass =>
        for {
          fieldsJson <- cursor.as[Map[String, Json]]
          decodedFields <-
            fieldsJson.toList.traverse { case (fieldName, fieldJson) =>
              val fieldType = fields.getOrElse(fieldName, Unknown)
              decodeValue(fieldType, fieldJson.hcursor).map(fieldName -> _)
            }
          javaMap = decodedFields.toMap.asJava
        } yield javaMap
      case TypedObjectTypingResult(_, nonMapRuntimeObjType, _) =>
        // To decode other types than Map (Avro's GenericRecords or Table APIs Row), we should add schema to type
        handleDecodingError(
          s"Decoding of non-Map based records (runtime type: ${nonMapRuntimeObjType.display}) is not supported.",
          cursor
        )
      case unknown @ Unknown(_) =>
        /// For Unknown we fallback to generic json to any conversion. It won't work for some types such as LocalDate but for others should work correctly
        cursor.as[Json].map { json =>
          val result = FromJsonSimpleDecoder.jsonToAny(json)
          logger.debug(
            s"Target type for json [${json.noSpaces}] decoding is [$unknown] type. For decoding was used simple decoder. Result is [$result]"
          )
          result
        }
      case typ => handleDecodingError(s"Decoding of type [$typ] is not supported.", cursor)
    }
  }

  private def convertToArray(list: List[Any], elementType: TypingResult) = elementType match {
    case single: SingleTypingResult =>
      val reflectiveCreatedArray =
        java.lang.reflect.Array.newInstance(single.runtimeObjType.klass, list.size).asInstanceOf[Array[Any]]
      // noinspection ScalaUnusedExpression
      list.copyToArray(
        reflectiveCreatedArray
      )
      reflectiveCreatedArray
    case _ =>
      list.toArray[Any]
  }

  private def handleExceptionAsDecodingFailure[I, O](cursor: HCursor)(f: I => O): I => Decoder.Result[O] =
    (input: I) => {
      try {
        Right(f(input))
      } catch {
        case NonFatal(ex) =>
          handleDecodingError(ex.getMessage, cursor)
      }
    }

  private def handleDecodingError(message: String, cursor: HCursor): Decoder.Result[Nothing] = {
    Left(DecodingFailure(message, cursor.history))
  }

}
