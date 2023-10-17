package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.scenariodetails.EngineSetupName

object ProcessingTypeSetupsProvider {

  def processingTypeSetups(
      processingTypesData: Map[ProcessingType, ProcessingTypeData]
  ): Map[ProcessingType, ProcessingTypeSetup] = processingTypesData.mapValuesNow { data =>
    val singleProcessingMode = data.modelData.componentsProcessingModesInterception.toList match {
      case oneMode :: Nil => oneMode
      case Nil            => ???
      case moreThenOne    => ???
    }
    ProcessingTypeSetup(singleProcessingMode, EngineSetupName("TODO"))
  }

}
