package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.engine.api.process.WithCategories.anyCategory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class DefaultConfigCreator extends EmptyProcessConfigCreator {

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      Map(
        "GEO" -> anyCategory(geo),
        "NUMERIC" -> anyCategory(numeric),
        "CONV" -> anyCategory(conversion),
        "DATE" -> anyCategory(date),
        "DATE_FORMAT" -> anyCategory(dateFormat),
        "UTIL" -> anyCategory(util),
        "MATH" -> anyCategory(math),
      ),
      List()
    )
  }

  override def buildInfo(): Map[String, String] = {
    pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => k -> v.toString } + ("name" -> "defaultModel")
  }

}
