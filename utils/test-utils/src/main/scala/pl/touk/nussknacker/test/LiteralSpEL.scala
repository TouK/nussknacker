package pl.touk.nussknacker.test

import java.util
import LiteralSpEL._

object LiteralSpEL {
  val Input: SpecialSpELElement = SpecialSpELElement("#input")
  val EmptyRecord = "{:}"
  val EmptyList: String = "{}"
}

trait LiteralSpEL {

  import collection.JavaConverters._

  protected def toSpELLiteral(data: Any): String = {
    def convertCollection(data: List[String]) = s"""{${data.mkString(",")}}"""

    data match {
      case map: collection.Map[String@unchecked, _] if map.isEmpty =>
        EmptyRecord
      case map: collection.Map[String@unchecked, _] =>
        val elements = map.map{case (key, value) => s""""$key": ${toSpELLiteral(value)}"""}
        convertCollection(elements.toList)
      case map: util.Map[String@unchecked, _] =>
        toSpELLiteral(map.asScala)
      case collection: Traversable[_] if collection.isEmpty =>
        EmptyList
      case collection: Traversable[_] =>
        val elements = collection.toList.map(v => toSpELLiteral(v))
        convertCollection(elements)
      case collection: util.Collection[_] =>
        toSpELLiteral(collection.asScala)
      case element: SpecialSpELElement => element.value
      case spel: String if spel.startsWith("T(") => spel
      case spel: String if spel.startsWith("{") => spel
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

case class SpecialSpELElement(value: String) extends AnyVal
