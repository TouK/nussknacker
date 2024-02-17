package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentWithDefinition
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition

class AlignedComponentsDefinitionProvider(
    builtInComponentsDefinitionsPreparer: BuiltInComponentsDefinitionsPreparer,
    fragmentComponentDefinitionExtractor: FragmentComponentDefinitionExtractor,
    modelDefinition: ModelDefinition
) {

  def getAlignedComponentsWithBuiltInComponentsAndFragments(
      forFragment: Boolean,
      fragments: List[CanonicalProcess],
  ): List[ComponentWithDefinition] = {
    val filteredModel = if (forFragment) {
      modelDefinition
        .filterComponents(_.componentType != ComponentType.Source)
    } else {
      modelDefinition
    }

    val builtInComponents =
      builtInComponentsDefinitionsPreparer.prepareDefinitions(forFragment)
    val fragmentComponents =
      // TODO: Support for fragments using other fragments
      if (forFragment) List.empty
      else extractFragmentComponents(fragments)

    filteredModel
      .withComponents(builtInComponents)
      .withComponents(fragmentComponents)
      .components
  }

  private def extractFragmentComponents(
      fragmentsScenarios: List[CanonicalProcess],
  ): List[ComponentWithDefinition] =
    for {
      scenario   <- fragmentsScenarios
      definition <- fragmentComponentDefinitionExtractor.extractFragmentComponentDefinition(scenario).toOption
    } yield definition

}

object AlignedComponentsDefinitionProvider {

  def apply(modelData: ModelData): AlignedComponentsDefinitionProvider = {
    new AlignedComponentsDefinitionProvider(
      new BuiltInComponentsDefinitionsPreparer(modelData.componentsUiConfig),
      new FragmentComponentDefinitionExtractor(
        modelData.modelClassLoader.classLoader,
        modelData.componentsUiConfig.groupName,
        modelData.determineDesignerWideId
      ),
      modelData.modelDefinition
    )
  }

}
