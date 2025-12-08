package pl.touk.nussknacker.ui.process.test.testcase

import java.util.Objects
import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}

object SpelValuePrettyPrinter {

  def prettyPrintValue(value: Any): String = {
    value match {
      case array: Array[_] => array.map(prettyPrintValue).mkString("{", ", ", "}")
      case map: java.util.Map[_, _] if map.isEmpty => "{:}"
      case map: java.util.Map[_, _] => map.asScala.map { case (k, v) => prettyPrintValue(k) + ": " + prettyPrintValue(v) }.mkString("{", ", ", "}")
      case collection: java.util.Collection[_] => collection.asScala.map(prettyPrintValue).mkString("{", ", ", "}")
      case _ => Objects.toString(value)
    }
  }

}
