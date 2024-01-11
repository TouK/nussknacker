package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.process.ProcessingType

object ComponentIdProviderFactory {

  def create(
      processingTypeDataMap: Map[ProcessingType, ProcessingTypeData],
  ): ComponentIdProvider = {
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
