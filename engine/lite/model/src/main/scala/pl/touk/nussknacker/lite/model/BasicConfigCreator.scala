package pl.touk.nussknacker.lite.model

import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.util.functions._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class BasicConfigCreator extends EmptyProcessConfigCreator {

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val categories = processObjectDependencies.config.getAs[List[String]]("categories").getOrElse("Default" :: Nil)
    def withCategories[T](obj: T): WithCategories[T] = WithCategories(obj, categories: _*)

    ExpressionConfig(
      Map(
        "GEO" -> withCategories(geo),
        "NUMERIC" -> withCategories(numeric),
        "CONV" -> withCategories(conversion),
        "DATE" -> withCategories(date),
        "DATE_FORMAT" -> withCategories(dateFormat),
        "UTIL" -> withCategories(util),
        "MATH" -> withCategories(math),
      ),
      List()
    )
  }

  override def buildInfo(): Map[String, String] = {
    pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => k -> v.toString } + ("name" -> "basic")
  }

}
