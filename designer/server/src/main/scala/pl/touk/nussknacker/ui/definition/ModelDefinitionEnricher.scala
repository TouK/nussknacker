package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsStaticDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails

class ModelDefinitionEnricher(builtInComponentsConfig: ComponentsUiConfig, modelClassLoader: ClassLoader) {

  private val builtInComponentsDefinitionsPreparer = new BuiltInComponentsStaticDefinitionsPreparer(
    builtInComponentsConfig
  )

  def enrichModelDefinitionWithBuiltInComponentsAndFragments(
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
      forFragment: Boolean,
      fragmentsDetails: List[FragmentDetails]
  ): ModelDefinition[ComponentStaticDefinition] = {
    val builtInComponents =
      builtInComponentsDefinitionsPreparer.prepareStaticDefinitions(forFragment)
    val fragmentComponents =
      // TODO: Support for fragments using other fragments
      if (forFragment) List.empty
      else extractFragmentComponents(modelClassLoader, fragmentsDetails)
    modelDefinition
      .withComponents(builtInComponents)
      .withComponents(fragmentComponents.toList)
  }

  // FIXME: use FragmentsStaticDefinitionPreparer instead
  private def extractFragmentComponents(
      classLoader: ClassLoader,
      fragmentsDetails: List[FragmentDetails],
  ): Map[String, ComponentStaticDefinition] = {
    val definitionExtractor = new FragmentWithoutValidatorsDefinitionExtractor(classLoader)
    (for {
      details    <- fragmentsDetails
      definition <- definitionExtractor.extractFragmentComponentDefinition(details.canonical).toOption
    } yield {
      details.canonical.name.value -> definition.toStaticDefinition(details.category)
    }).toMap
  }

}
