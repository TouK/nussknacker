package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser

class ModelDefinitionAligner(
                               builtInComponentsDefinitionsPreparer: BuiltInComponentsDefinitionsPreparer,
                               fragmentComponentDefinitionExtractor: FragmentComponentDefinitionExtractor,
                               modelDefinition: ModelDefinition
) {

  def getAlignedModelDefinitionWithBuiltInComponentsAndFragments(
      forFragment: Boolean,
      fragmentScenarios: List[CanonicalProcess],
  ): ModelDefinition = {
    val filteredModel = if (forFragment) {
      modelDefinition
        .filterComponents((componentInfo, _) => componentInfo.`type` != ComponentType.Source)
    } else {
      modelDefinition
    }

    val builtInComponents =
      builtInComponentsDefinitionsPreparer.prepareDefinitions(forFragment)
    val fragmentComponents =
      // TODO: Support for fragments using other fragments
      if (forFragment) List.empty
      else extractFragmentComponents(fragmentScenarios)

    filteredModel
      .withComponents(builtInComponents)
      .withComponents(fragmentComponents.toList)
  }

  private def extractFragmentComponents(
      fragmentsScenarios: List[CanonicalProcess],
  ): Map[String, ComponentDefinitionWithImplementation] = {
    (for {
      scenario   <- fragmentsScenarios
      definition <- fragmentComponentDefinitionExtractor.extractFragmentComponentDefinition(scenario).toOption
    } yield {
      scenario.name.value -> definition
    }).toMap
  }

}

object ModelDefinitionAligner {

  def apply(modelData: ModelData): ModelDefinitionAligner = {
    val builtInComponentConfig = ComponentsUiConfigParser.parse(modelData.modelConfig)
    new ModelDefinitionAligner(
      new BuiltInComponentsDefinitionsPreparer(builtInComponentConfig),
      new FragmentComponentDefinitionExtractor(modelData.modelClassLoader.classLoader, modelData.componentInfoToId),
      modelData.modelDefinition
    )
  }

}
