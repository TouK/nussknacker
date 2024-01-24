package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.MetaDataInitializer
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.NewProcessPreparer.initialFragmentFields

object NewProcessPreparer {

  private val initialFragmentFields: ProcessAdditionalFields = ProcessAdditionalFields(
    None,
    Map(FragmentSpecificData.docsUrlName -> ""),
    FragmentSpecificData.typeName
  )

}

class NewProcessPreparer(creator: MetaDataInitializer, scenarioProperties: Map[String, ScenarioPropertyConfig]) {

  def prepareEmptyProcess(processName: ProcessName, isFragment: Boolean): CanonicalProcess = {
    val initialProperties = scenarioProperties.map { case (key, config) =>
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
