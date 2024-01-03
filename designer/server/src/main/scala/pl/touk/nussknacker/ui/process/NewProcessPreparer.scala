package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.{MetaDataInitializer, ProcessingTypeData}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.NewProcessPreparer.initialFragmentFields
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

object NewProcessPreparer {

  def apply(
      processingTypesData: ProcessingTypeDataProvider[ProcessingTypeData, _],
      additionalFields: ProcessingTypeDataProvider[Map[String, ScenarioPropertyConfig], _]
  ): NewProcessPreparer =
    new NewProcessPreparer(processingTypesData.mapValues(_.metaDataInitializer), additionalFields)

  private val initialFragmentFields: ProcessAdditionalFields = ProcessAdditionalFields(
    None,
    Map(FragmentSpecificData.docsUrlName -> ""),
    FragmentSpecificData.typeName
  )

}

class NewProcessPreparer(
    emptyProcessCreate: ProcessingTypeDataProvider[MetaDataInitializer, _],
    additionalFields: ProcessingTypeDataProvider[Map[String, ScenarioPropertyConfig], _]
) {

  def prepareEmptyProcess(processName: ProcessName, processingType: ProcessingType, isFragment: Boolean)(
      implicit user: LoggedUser
  ): CanonicalProcess = {
    val creator = emptyProcessCreate.forTypeUnsafe(processingType)
    val initialProperties = additionalFields.forTypeUnsafe(processingType).map { case (key, config) =>
      (key, config.defaultValue.getOrElse(""))
    }
    val initialMetadata =
      if (isFragment) MetaData(processName.value, initialFragmentFields)
      else creator.create(processName, initialProperties)

    val emptyCanonical = CanonicalProcess(
      metaData = initialMetadata,
      nodes = List.empty,
      additionalBranches = List.empty
    )
    emptyCanonical
  }

}
