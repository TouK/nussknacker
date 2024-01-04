package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsStaticDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails

class ModelDefinitionEnricher(
    builtInComponentsDefinitionsPreparer: BuiltInComponentsStaticDefinitionsPreparer,
    fragmentDefinitionExtractor: FragmentWithoutValidatorsDefinitionExtractor,
    additionalUIConfigFromProviderEnricher: AdditionalUIConfigFinalizer,
    modelDefinition: ModelDefinition[ComponentStaticDefinition]
) {

  def modelDefinitionWithBuiltInComponentsAndFragments(
      forFragment: Boolean,
      fragmentsDetails: List[FragmentDetails],
      processingType: ProcessingType
  ): ModelDefinition[ComponentStaticDefinition] = {
    val builtInComponents =
      builtInComponentsDefinitionsPreparer.prepareStaticDefinitions(forFragment)
    val fragmentComponents =
      // TODO: Support for fragments using other fragments
      if (forFragment) List.empty
      else extractFragmentComponents(fragmentsDetails)
    additionalUIConfigFromProviderEnricher.finalizeModelDefinition(
      modelDefinition
        .withComponents(builtInComponents)
        .withComponents(fragmentComponents.toList),
      processingType
    )
  }

  private def extractFragmentComponents(
      fragmentsDetails: List[FragmentDetails],
  ): Map[String, ComponentStaticDefinition] = {
    (for {
      details    <- fragmentsDetails
      definition <- fragmentDefinitionExtractor.extractFragmentComponentDefinition(details.canonical).toOption
    } yield {
      details.canonical.name.value -> definition.toStaticDefinition(details.category)
    }).toMap
  }

}

object ModelDefinitionEnricher {

  def apply(
      modelData: ModelData,
      additionalUIConfigFromProviderEnricher: AdditionalUIConfigFinalizer,
      modelDefinition: ModelDefinition[ComponentStaticDefinition]
  ): ModelDefinitionEnricher = {
    val builtInComponentConfig = ComponentsUiConfigParser.parse(modelData.modelConfig)
    new ModelDefinitionEnricher(
      new BuiltInComponentsStaticDefinitionsPreparer(builtInComponentConfig),
      new FragmentWithoutValidatorsDefinitionExtractor(modelData.modelClassLoader.classLoader),
      additionalUIConfigFromProviderEnricher,
      modelDefinition
    )
  }

}
