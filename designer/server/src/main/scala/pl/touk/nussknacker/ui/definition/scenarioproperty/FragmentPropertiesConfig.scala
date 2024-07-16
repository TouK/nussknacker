package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.FragmentSpecificData
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  StringParameterEditor
}

object FragmentPropertiesConfig {

  val docsUrlConfig: (String, ScenarioPropertyConfig) = FragmentSpecificData.docsUrlName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      // TODO: some validator
      validators = None,
      label = Some("Documentation url")
    )

  val isDeprecatedConfig: (String, ScenarioPropertyConfig) = FragmentSpecificData.isDeprecatedName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(
        FixedValuesParameterEditor(List(FixedExpressionValue("true", "True"), FixedExpressionValue("false", "False")))
      ),
      validators = None,
      label = Some("Deprecated")
    )

  // TODO: We should probably allow to add some properties definition using configuration like in the scenario case
  val properties: Map[String, ScenarioPropertyConfig] = Map(docsUrlConfig, isDeprecatedConfig)

}
