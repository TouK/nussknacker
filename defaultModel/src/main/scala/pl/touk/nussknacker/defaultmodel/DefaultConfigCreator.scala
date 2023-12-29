package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.engine.api.process.WithCategories.anyCategory
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, _}

class DefaultConfigCreator extends EmptyProcessConfigCreator {

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      Map(
        "GEO"         -> anyCategory(geo),
        "NUMERIC"     -> anyCategory(numeric),
        "CONV"        -> anyCategory(conversion),
        "COLLECTION"  -> anyCategory(collection),
        "DATE"        -> anyCategory(date),
        "DATE_FORMAT" -> anyCategory(dateFormat),
        "UTIL"        -> anyCategory(util),
      ),
      List()
    )
  }

  override def buildInfo(): Map[String, String] = {
    pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) =>
      k -> v.toString
    } + ("name" -> "defaultModel")
  }

}
