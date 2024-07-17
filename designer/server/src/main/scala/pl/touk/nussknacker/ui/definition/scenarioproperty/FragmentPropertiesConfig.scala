package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.FragmentSpecificData
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.StringParameterEditor

object FragmentPropertiesConfig {

  val docsUrlConfig: (String, ScenarioPropertyConfig) = FragmentSpecificData.docsUrlName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      // TODO: some validator
      validators = None,
      label = Some("Documentation url"),
      hintText = None
    )

  // TODO: We should probably allow to add some properties definition using configuration like in the scenario case
  val properties: Map[String, ScenarioPropertyConfig] = Map(docsUrlConfig)

}
