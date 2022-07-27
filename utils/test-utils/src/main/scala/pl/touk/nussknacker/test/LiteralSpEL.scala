package pl.touk.nussknacker.test

import java.util
import LiteralSpEL._

import java.time.{Instant, LocalDate, LocalTime}

object LiteralSpEL {
  val Input: SpecialSpELElement = SpecialSpELElement("#input")
  val EmptyRecord: String = "{:}"
}

trait LiteralSpEL {

  import collection.JavaConverters._
  protected def toSpELLiteral(data: Any): String = {

    def convertCollection(data: List[String]) = s"""{${data.mkString(", ")}}"""

    data match {
      case map: collection.Map[String@unchecked, _] if map.isEmpty =>
        EmptyRecord
      case map: collection.Map[String@unchecked, _] =>
        val elements = map.map{case (key, value) => s""""$key": ${toSpELLiteral(value)}"""}
        convertCollection(elements.toList)
      case map: util.Map[String@unchecked, _] =>
        toSpELLiteral(map.asScala)
      case collection: Traversable[_] =>
        val elements = collection.toList.map(v => toSpELLiteral(v))
        convertCollection(elements)
      case collection: util.Collection[_] =>
        toSpELLiteral(collection.asScala)
      case element: SpecialSpELElement => element.value
      case str: String => s"'$str'"
      case long: Long => s"${long}l"
      case db: Double => s"${db}d"
      case fl: Float => s"${fl}f"
      case null => "null"
      case v => v.toString
    }
  }

}

object LiteralSpELImplicits {
  implicit class LiteralSpELImplicits(data: Any) extends LiteralSpEL {
    def toSpELLiteral: String = toSpELLiteral(data)
  }
}

final case class SpecialSpELElement(value: String) extends AnyVal

object SpecialSpELElement {

  def double(value: Any): SpecialSpELElement =
    SpecialSpELElement(s"T(java.lang.Double).valueOf($value)")

  def bigDecimal(value: Long, scale: Int): SpecialSpELElement =
    SpecialSpELElement(s"T(java.math.BigDecimal).valueOf(${value}l).setScale($scale)")

  def uuid(uuid: String): SpecialSpELElement =
    SpecialSpELElement(s"""T(java.util.UUID).fromString("$uuid")""")

  def localTime(localTime: LocalTime): SpecialSpELElement =
    SpecialSpELElement(s"T(java.time.LocalTime).ofNanoOfDay(${localTime.toNanoOfDay}l)")

  def localDate(localDate: LocalDate): SpecialSpELElement =
    SpecialSpELElement(s"T(java.time.LocalDate).ofEpochDay(${localDate.toEpochDay})")

  def instant(instant: Instant): SpecialSpELElement =
    SpecialSpELElement(s"T(java.time.Instant).ofEpochMilli(${instant.toEpochMilli}l)")

  def instant(epochSecond: Long, nanoAdjustment: Long): SpecialSpELElement =
    SpecialSpELElement(s"T(java.time.Instant).ofEpochSecond(${epochSecond}l, ${nanoAdjustment}l)")

}
