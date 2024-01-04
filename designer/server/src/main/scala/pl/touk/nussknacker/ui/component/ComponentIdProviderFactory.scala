package pl.touk.nussknacker.ui.component

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.ComponentInfo
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessCategoryService

object ComponentIdProviderFactory extends LazyLogging {

  def createUnsafe(
      processingTypeDataMap: Map[ProcessingType, ProcessingTypeData],
      categoryService: ProcessCategoryService
  ): ComponentIdProvider = {
    logger.debug("Creating component id provider")
    val componentIdProvider = new DefaultComponentIdProvider({ (processingType, info) =>
      processingTypeDataMap
        .get(processingType)
        .map(_.staticModelDefinition)
        .flatMap(_.getComponent(info))
        .map(_.componentConfig)
    })
    componentIdProvider
  }

}
