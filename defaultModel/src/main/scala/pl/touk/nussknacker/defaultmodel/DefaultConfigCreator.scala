package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class DefaultConfigCreator extends EmptyProcessConfigCreator {

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "CONV" -> defaultCategory(conversion),
        "DATE" -> defaultCategory(date),
        "DATE_FORMAT" -> defaultCategory(dateFormat),
        "UTIL" -> defaultCategory(util),
        "MATH" -> defaultCategory(math),
      ),
      List()
    )
  }

  override def buildInfo(): Map[String, String] = {
    pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => k -> v.toString } + ("name" -> "defaultModel")
  }

}
