package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.{MetaDataInitializer, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.NewProcessPreparer.initialFragmentFields
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

object NewProcessPreparer {

  def apply(processTypes: ProcessingTypeDataProvider[ProcessingTypeData, _], additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]): NewProcessPreparer =
    new NewProcessPreparer(processTypes.mapValues(_.metaDataInitializer), additionalFields)

  private val initialFragmentFields: ProcessAdditionalFields = ProcessAdditionalFields(
    None,
    Map(FragmentSpecificData.docsUrlName -> ""),
    FragmentSpecificData.typeName
  )

}


class NewProcessPreparer(emptyProcessCreate: ProcessingTypeDataProvider[MetaDataInitializer, _],
                         additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isFragment: Boolean): CanonicalProcess = {
    val creator = emptyProcessCreate.forTypeUnsafe(processingType)
    val initialProperties = additionalFields.forTypeUnsafe(processingType).map {
      case (key, config) => (key, config.defaultValue.getOrElse(""))
    }
    val name = ProcessName(processId)
    val initialMetadata = if (isFragment) MetaData(name.value, initialFragmentFields) else creator.create(name, initialProperties)

    val emptyCanonical = CanonicalProcess(
      metaData = initialMetadata,
      nodes = List.empty,
      additionalBranches = List.empty
    )
    emptyCanonical
  }

}
