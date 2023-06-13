package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.{ProcessingTypeData, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, RequestResponseMetaData, ScenarioSpecificData, TypeSpecificData}
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
        additionalFields = defaultAdditionalFields(processingType, typeSpecificData)
      ),
      nodes = List.empty,
      additionalBranches = List.empty
    )
    emptyCanonical
  }

  private def defaultAdditionalFields(processingType: ProcessingType, typeSpecificData: TypeSpecificData): ProcessAdditionalFields = {
    ProcessAdditionalFields(None, properties = defaultProperties(processingType, typeSpecificData), typeSpecificData.metaDataType)
  }

  private def defaultProperties(processingType: ProcessingType,
                                typeSpecificData: TypeSpecificData): Map[String, String] = {
    val configOutsideProviderConfig = typeSpecificData.toMap
    typeSpecificData match {
      case _: ScenarioSpecificData => {
        val configFromConfigProvider = additionalFields.forTypeUnsafe(processingType).map(s => s._1 -> s._2.defaultValue.getOrElse(""))
        // We overwrite defaults from additionalPropertiesConfig by typeSpecificInitialData
        configFromConfigProvider ++ configOutsideProviderConfig
      }
      // We don't add additional properties to fragments
      case FragmentSpecificData(_) => typeSpecificData.toMap
    }
  }

}
