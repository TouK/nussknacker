package pl.touk.nussknacker.test

import pl.touk.nussknacker.test.SpecialSpELElement.EmptyMap

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util
import java.util.UUID

trait LiteralSpEL {

  import scala.jdk.CollectionConverters._
  protected def toSpELLiteral(data: Any): String = {

    def convertCollection(data: List[String]) = s"""{${data.mkString(", ")}}"""

    data match {
      case map: collection.Map[String@unchecked, _] if map.isEmpty =>
        EmptyMap.value
      case map: collection.Map[String@unchecked, _] =>
        val elements = map.map{case (key, value) => s""""$key": ${toSpELLiteral(value)}"""}
        convertCollection(elements.toList)
      case map: util.Map[String@unchecked, _] =>
        toSpELLiteral(map.asScala)
      case collection: Iterable[_] =>
        val elements = collection.toList.map(v => toSpELLiteral(v))
        convertCollection(elements)
      case collection: util.Collection[_] =>
        toSpELLiteral(collection.asScala)
      case element: SpecialSpELElement => element.value
      case ldt: LocalDateTime => s"T(java.time.LocalDateTime).parse('${ldt.toString}')"
      case ltm: LocalTime => s"T(java.time.LocalTime).parse('${ltm.toString}')"
      case ld: LocalDate =>s"T(java.time.LocalDate).parse('${ld.toString}')"
      case ins: Instant => s"T(java.time.Instant).parse('${ins.toString}')"
      case uuid: UUID => s"T(java.util.UUID).fromString('${uuid.toString}')"
      case bd: BigDecimal => s"T(java.math.BigDecimal).valueOf(${bd.doubleValue}).setScale(${bd.scale})"
      case bd: java.math.BigDecimal => s"T(java.math.BigDecimal).valueOf(${bd.doubleValue}).setScale(${bd.scale})"
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

  val Input: SpecialSpELElement = SpecialSpELElement("#input")
  val EmptyMap: SpecialSpELElement = SpecialSpELElement("{:}")

}
