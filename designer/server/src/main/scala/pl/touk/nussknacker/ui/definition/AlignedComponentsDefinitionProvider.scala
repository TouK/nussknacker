package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.{ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithImplementation, Components}
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData

class AlignedComponentsDefinitionProvider(
    builtInComponentsDefinitionsPreparer: BuiltInComponentsDefinitionsPreparer,
    fragmentComponentDefinitionExtractor: FragmentComponentDefinitionExtractor,
    modelDefinition: ModelDefinition,
    processingMode: ProcessingMode
) {

  def getAlignedComponentsWithBuiltInComponentsAndFragments(
      forFragment: Boolean,
      fragments: List[CanonicalProcess],
  ): Components = {
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
  ): List[ComponentDefinitionWithImplementation] =
    for {
      scenario <- fragmentsScenarios
      definition <- fragmentComponentDefinitionExtractor
        .extractFragmentComponentDefinition(scenario, AllowedProcessingModes.SetOf(processingMode))
        .toOption
    } yield definition

}

object AlignedComponentsDefinitionProvider {

  def apply(designerModelData: DesignerModelData): AlignedComponentsDefinitionProvider = {
    new AlignedComponentsDefinitionProvider(
      new BuiltInComponentsDefinitionsPreparer(designerModelData.modelData.componentsUiConfig),
      new FragmentComponentDefinitionExtractor(
        designerModelData.modelData.modelClassLoader,
        designerModelData.modelData.modelDefinitionWithClasses.classDefinitions,
        designerModelData.modelData.componentsUiConfig.groupName,
        designerModelData.modelData.determineDesignerWideId
      ),
      designerModelData.modelData.modelDefinition,
      designerModelData.processingMode
    )
  }

}
