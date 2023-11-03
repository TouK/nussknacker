package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.processingtypesetup.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object ProcessingTypeSetupsProvider {

  def processingTypeSetups(
      processingTypesData: Map[ProcessingType, ProcessingTypeData]
  ): Map[ProcessingType, ProcessingTypeSetup] = processingTypesData.mapValuesNow { data =>
    val singleProcessingMode = data.modelData.componentsProcessingModesInterception.toList match {
      case oneMode :: Nil => oneMode
      case Nil            => ???
      case moreThenOne    => ???
    }
    ProcessingTypeSetup(singleProcessingMode, EngineSetup(EngineSetupName("TODO"), List.empty))
  }

  def engineSetups(deploymentManagersData: Map[ProcessingType, EngineSetupData]): Map[ProcessingType, EngineSetup] = ???

  case class EngineSetupData(
      defaultEngineSetupName: EngineSetupName,
      setupIdentity: Any,
      setupErrors: List[String],
      specifiedEngineSetupName: Option[EngineSetupName]
  )

}
