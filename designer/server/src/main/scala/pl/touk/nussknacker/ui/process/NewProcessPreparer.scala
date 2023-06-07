package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.{ProcessingTypeData, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

object NewProcessPreparer {

  def apply(processTypes: ProcessingTypeDataProvider[ProcessingTypeData, _], additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]): NewProcessPreparer =
    new NewProcessPreparer(processTypes.mapValues(_.typeSpecificInitialData), additionalFields)

}


class NewProcessPreparer(emptyProcessCreate: ProcessingTypeDataProvider[TypeSpecificInitialData, _],
                         additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean): CanonicalProcess = {
    val creator = emptyProcessCreate.forTypeUnsafe(processingType)
    val specificMetaData = if(isSubprocess) creator.forFragment _ else creator.forScenario _
    val typeSpecificData = specificMetaData(ProcessName(processId), processingType)
    val emptyCanonical = CanonicalProcess(
      metaData = MetaData(
        id = processId,
        typeSpecificData = typeSpecificData,
        additionalFields = defaultAdditionalFields(processingType, typeSpecificData.scenarioType)
      ),
      nodes = List.empty,
      additionalBranches = List.empty
    )
    emptyCanonical
  }

  // TODO: new typespecific defaults based on config
  private def defaultAdditionalFields(processingType: ProcessingType, scenarioType: String): ProcessAdditionalFields = {
    ProcessAdditionalFields(None, properties = defaultProperties(processingType), scenarioType)
  }

  private def defaultProperties(processingType: ProcessingType): Map[String, String] = additionalFields.forTypeUnsafe(processingType)
    .collect { case (name, parameterConfig) if parameterConfig.defaultValue.isDefined => name -> parameterConfig.defaultValue.get }
}
