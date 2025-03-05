package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, _}
import pl.touk.nussknacker.engine.api.process.WithCategories.anyCategory

class DefaultConfigCreator extends EmptyProcessConfigCreator {

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      globalProcessVariables = Map(
        "GEO"         -> anyCategory(geo),
        "NUMERIC"     -> anyCategory(numeric),
        "CONV"        -> anyCategory(conversion),
        "COLLECTION"  -> anyCategory(collection),
        "DATE"        -> anyCategory(date),
        "DATE_FORMAT" -> anyCategory(dateFormat),
        "UTIL"        -> anyCategory(util),
        "RANDOM"      -> anyCategory(random),
        "BASE64"      -> anyCategory(base64)
      ),
      globalImports = List()
    )
  }

  override def modelInfo(): ModelInfo = {
    ModelInfo.fromMap(
      pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) =>
        k -> v.toString
      } + ("name" -> "defaultModel")
    )
  }

}
