package pl.touk.nussknacker.test

import java.util

trait SinkOutputSpELConverter {

  import collection.JavaConverters._

  val Input: String = "#input"
  val EmptyRecord = "{:}"
  val EmptyList = "{}"
  val EmptyRoot = "#:#"

  def convertToMap(field: String, data: Any): String = {
    def record(value: Any) = s"""{"$field": $value}"""

    data match {
      case str: String if str == Input => str
      case str: String if str == EmptyRoot => EmptyRecord
      case any => record(convert(any, isField = true))
    }
  }

  def convert(data: Any, isField: Boolean = false): String = {
    def convertCollection(data: List[String]) = s"""{${data.mkString(",")}}"""

    data match {
      case map: collection.Map[String@unchecked, _] if map.isEmpty =>
        EmptyRecord
      case map: collection.Map[String@unchecked, _] =>
        val elements = map.map{case (key, value) => s""""$key": ${convert(value, isField = true)}"""}
        convertCollection(elements.toList)
      case map: util.Map[String@unchecked, _] =>
        convert(map.asScala)
      case collection: Traversable[_] if collection.isEmpty =>
        EmptyList
      case collection: Traversable[_] =>
        val elements = collection.toList.map(v => convert(v, isField = true))
        convertCollection(elements)
      case collection: util.Collection[_] =>
        convert(collection.asScala)
      case str: String if str == Input => str
      case spel: String if spel.startsWith("T(") => spel
      case spel: String if spel.startsWith("{") => spel
      case str: String if isField => s""""$str""""
      case str: String if !isField => s"'$str'"
      case long: Long => s"${long}l"
      case db: Double => s"${db}d"
      case fl: Float => s"${fl}f"
      case null => "null"
      case v => v.toString
    }
  }

}

object SinkOutputSpELConverter extends SinkOutputSpELConverter
