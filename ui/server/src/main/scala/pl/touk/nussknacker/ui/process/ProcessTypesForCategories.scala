package pl.touk.nussknacker.ui.process

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

import scala.collection.JavaConverters._
import net.ceedubs.ficus.Ficus._


class ProcessTypesForCategories(config: Config) {

  private val categoriesToTypesMap = {
    val categories = config.getOrElse("categoriesConfig", ConfigFactory.parseMap(Collections.singletonMap("Default", "streaming")))
    categories.entrySet().asScala.map(_.getKey).map(category => category -> categories.getString(category)).toMap
  }

  def getTypeForCategory(category: String) : Option[ProcessingType] = {
    categoriesToTypesMap.get(category)
  }

  val getAllCategories = categoriesToTypesMap.keys.toList
}
