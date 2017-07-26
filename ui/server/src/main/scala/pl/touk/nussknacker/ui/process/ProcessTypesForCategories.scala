package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import scala.collection.JavaConversions._

class ProcessTypesForCategories(config: Config) {

  private val categoriesToTypesMap = {
    val categories = config.getConfig("categoriesConfig")
    categories.entrySet().map(_.getKey).map(category => category ->
      ProcessingType.withName(categories.getString(category))).toMap
  }

  def getTypeForCategory(category: String) : Option[ProcessingType] = {
    categoriesToTypesMap.get(category)
  }

}
