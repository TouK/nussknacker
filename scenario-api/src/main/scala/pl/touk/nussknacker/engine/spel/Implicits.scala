package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpELImplicits.{EmptyList, EmptyRecord, Input}

import java.util
import scala.language.implicitConversions

object SpELImplicits {
  val Input: String = "#input"
  val EmptyRecord: String = "{:}"
  val EmptyList: String = "{}"
}

trait SpELImplicits {

  import collection.JavaConverters._

  def toSpEL(data: Any): String = {
    def convertCollection(data: List[String]) = s"""{${data.mkString(",")}}"""

    data match {
      case map: collection.Map[String@unchecked, _] if map.isEmpty =>
        EmptyRecord
      case map: collection.Map[String@unchecked, _] =>
        val elements = map.map{case (key, value) => s""""$key": ${toSpEL(value)}"""}
        convertCollection(elements.toList)
      case map: util.Map[String@unchecked, _] =>
        toSpEL(map.asScala)
      case collection: Traversable[_] if collection.isEmpty =>
        EmptyList
      case collection: Traversable[_] =>
        val elements = collection.toList.map(v => toSpEL(v))
        convertCollection(elements)
      case collection: util.Collection[_] =>
        toSpEL(collection.asScala)
      case str: String if str == Input => str
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

object Implicits {

  implicit def asSpelExpression(expression: String): Expression =
    Expression(
      language = "spel",
      expression = expression
    )

  implicit class ImplicitsSpEL(data: Any) extends SpELImplicits {
    def toSpEL: String = toSpEL(data)
  }

}
