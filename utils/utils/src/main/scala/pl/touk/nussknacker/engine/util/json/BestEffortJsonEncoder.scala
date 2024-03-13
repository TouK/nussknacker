package pl.touk.nussknacker.engine.util.json

import io.circe.Encoder.encodeZonedDateTimeWithFormatter

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter
import io.circe.{Encoder, Json}
import io.circe.Json._
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.util.Implicits._

import java.util.ServiceLoader
import java.util.Set

import java.util.UUID
import scala.jdk.CollectionConverters._

object BestEffortJsonEncoder {

  val defaultForTests: BestEffortJsonEncoder = BestEffortJsonEncoder(failOnUnknown = true, getClass.getClassLoader)

}

case class BestEffortJsonEncoder(
    failOnUnknown: Boolean,
    classLoader: ClassLoader,
    highPriority: PartialFunction[Any, Json] = Map()
) {

  private val safeString     = safeJson[String](fromString)
  private val safeLong       = safeJson[Long](fromLong)
  private val safeInt        = safeJson[Int](fromInt)
  private val safeDouble     = safeJson[Double](fromDoubleOrNull)
  private val safeFloat      = safeJson[Float](fromFloatOrNull)
  private val safeBigDecimal = safeJson[java.math.BigDecimal](a => fromBigDecimal(a))
  private val safeBigInt     = safeJson[java.math.BigInteger](a => fromBigInt(a))
  private val safeNumber     = safeJson[Number](a => fromDoubleOrNull(a.doubleValue())) // is it correct?

  val circeEncoder: Encoder[Any] = Encoder.encodeJson.contramap(encode)

  private val optionalEncoders =
    ServiceLoader.load(classOf[ToJsonEncoder], classLoader).asScala.map(_.encoder(this.encode))

  def encode(obj: Any): Json = optionalEncoders
    .foldLeft(highPriority)(_.orElse(_))
    .applyOrElse(
      obj,
      (any: Any) =>
        any match {
          case null                    => Null
          case Some(a)                 => encode(a)
          case None                    => Null
          case j: Json                 => j
          case s: String               => safeString(s)
          case a: Long                 => safeLong(a)
          case a: Double               => safeDouble(a)
          case a: Float                => safeFloat(a)
          case a: Int                  => safeInt(a)
          case a: java.math.BigDecimal => safeBigDecimal(a)
          case a: BigDecimal           => safeBigDecimal(a.underlying())
          case a: java.math.BigInteger => safeBigInt(a)
          case a: BigInt               => safeBigInt(a.underlying())
          case a: Number               => safeNumber(a.doubleValue())
          case a: Boolean              => safeJson[Boolean](fromBoolean)(a)
          case a: LocalDate            => Encoder[LocalDate].apply(a)
          case a: LocalTime            => Encoder[LocalTime].apply(a)
          case a: LocalDateTime        => Encoder[LocalDateTime].apply(a)
          // Default implementation serializes to ISO_ZONED_DATE_TIME which is not handled well by some parsers...
          case a: ZonedDateTime  => encodeZonedDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME).apply(a)
          case a: Instant        => Encoder[Instant].apply(a)
          case a: OffsetDateTime => Encoder[OffsetDateTime].apply(a)
          case a: UUID           => safeString(a.toString)
          case a: DisplayJson    => a.asJson
          case a: java.util.Map[String @unchecked, _] if allTheKeysAreStrings(a.keySet()) => encodeMap(a.asScala.toMap)
          case a: scala.collection.Map[String @unchecked, _] if allTheKeysAreStrings(a.keySet) => encodeMap(a.toMap)
          case a: java.util.Map[_, _] if allTheKeysAreJavaMaps(a.keySet())       => encodeComplexJavaMap(a)
          case a: scala.collection.Map[_, _] if allTheKeysAreScalaMaps(a.keySet) => encodeComplexScalaMap(a)
          case a: Iterable[_]                                                    => fromValues(a.map(encode))
          case a: Enum[_]                                                        => safeString(a.toString)
          case a: java.util.Collection[_]                                        => fromValues(a.asScala.map(encode))
          case a: Array[_]                                                       => fromValues(a.map(encode))
          case _ if !failOnUnknown                                               => safeString(any.toString)
          case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}")
        }
    )

  private def safeJson[T](fun: T => Json) = (value: T) =>
    Option(value) match {
      case Some(realValue) => fun(realValue)
      case None            => Null
    }

  private def encodeComplexJavaMap(a: java.util.Map[_, _]) = {
    val castedMap = a.asInstanceOf[java.util.Map[java.util.Map[String, _], _]]
    val keys      = castedMap.keySet().asScala.toList
    val m = keys.map { keyMap =>
      val v              = castedMap.get(keyMap)
      val encodedKey     = encode(keyMap)
      val stringifiedKey = encodedKey.noSpaces
      (stringifiedKey, v)
    }.toMap

    encodeMap(m)
  }

  private def encodeComplexScalaMap(a: scala.collection.Map[_, _]) = {
    val castedMap = a.asInstanceOf[scala.collection.Map[scala.collection.Map[String, _], _]]
    val keys      = castedMap.keySet.toList

    val m = keys.map { keyMap =>
      val v: Any         = castedMap(keyMap)
      val encodedKey     = encode(keyMap)
      val stringifiedKey = encodedKey.noSpaces
      (stringifiedKey, v)
    }.toMap

    encodeMap(m)
  }

  private def encodeMap(map: Map[String, _]) = {
    fromFields(map.mapValuesNow(encode))
  }

  private def allTheKeysAreScalaMaps(keys: scala.collection.Set[_]): Boolean =
    keys.forall {
      case _: scala.collection.Map[String @unchecked, _] => true
      case _                                             => false
    }

  private def allTheKeysAreJavaMaps(keys: java.util.Set[_]): Boolean =
    keys.asScala.forall {
      case _: java.util.Map[String @unchecked, _] => true
      case _                                      => false
    }

  private def allTheKeysAreStrings(keys: java.util.Set[_]): Boolean =
    keys.asScala.forall {
      case _: String => true
      case _         => false
    }

  private def allTheKeysAreStrings(keys: scala.collection.Set[_]): Boolean =
    keys.forall {
      case _: String => true
      case _         => false
    }

}
