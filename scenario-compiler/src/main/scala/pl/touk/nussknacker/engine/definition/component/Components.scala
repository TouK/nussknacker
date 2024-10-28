package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.definition.model.DuplicatedComponentsException
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList

final case class Components private (
    components: List[ComponentDefinitionWithImplementation],
    // component definitions not enriched with ui config
    private val basicComponents: Option[List[ComponentDefinitionWithImplementation]]
) {

  def withComponents(componentsToAdd: List[ComponentDefinitionWithImplementation]): Components = {
    val newComponents = copy(
      components = components ::: componentsToAdd,
      basicComponents = basicComponents.map(values => values ::: componentsToAdd)
    )
    Components.validated(newComponents)
  }

  def withComponents(componentsToAdd: Components): Components = {
    val newComponents = Components.combine(this, componentsToAdd)
    Components.validated(newComponents)
  }

  def filter(predicate: ComponentDefinitionWithImplementation => Boolean): Components = {
    copy(
      components = components.filter(predicate),
      basicComponents = basicComponents.map(_.filter(predicate)),
    )
  }

  def basicComponentsUnsafe: List[ComponentDefinitionWithImplementation] =
    basicComponents.getOrElse(
      throw new IllegalStateException("Basic components requested but they are not precomputed")
    )

}

object Components {
  sealed abstract class ComponentDefinitionExtractionMode(val extractBasicDefinitions: Boolean)

  object ComponentDefinitionExtractionMode {
    case object FinalDefinition          extends ComponentDefinitionExtractionMode(false)
    case object FinalAndBasicDefinitions extends ComponentDefinitionExtractionMode(true)
  }

  def empty(mode: ComponentDefinitionExtractionMode): Components = new Components(
    components = List.empty,
    basicComponents = if (mode.extractBasicDefinitions) Some(List.empty) else None
  )

  def apply(
      components: List[ComponentDefinitionWithImplementation],
      basicComponents: Option[List[ComponentDefinitionWithImplementation]]
  ): Components = {
    val newComponents = new Components(components, basicComponents)
    validated(newComponents)
  }

  def withComponent(
      componentName: String,
      component: Component,
      configFromDefinition: ComponentConfig,
      componentsUiConfig: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): Components = {

    def extractComponentDefinition(
        uiConfig: ComponentsUiConfig,
        configFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
    ) = {
      ComponentDefinitionExtractor
        .extract(
          componentName,
          component,
          configFromDefinition,
          uiConfig,
          determineDesignerWideId,
          configFromProvider
        )
    }

    Components(
      components = extractComponentDefinition(componentsUiConfig, additionalConfigsFromProvider).toList,
      basicComponents =
        if (componentDefinitionExtractionMode.extractBasicDefinitions)
          Some(extractComponentDefinition(uiConfig = ComponentsUiConfig.Empty, configFromProvider = Map.empty).toList)
        else
          None
    )
  }

  def forList(
      components: List[ComponentDefinition],
      componentsUiConfig: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): Components = {

    def extractComponentDefinitions(
        uiConfig: ComponentsUiConfig,
        configFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
    ) = {
      ComponentDefinitionWithImplementation.forList(
        components,
        uiConfig,
        determineDesignerWideId,
        configFromProvider
      )
    }

    Components(
      components = extractComponentDefinitions(componentsUiConfig, additionalConfigsFromProvider).toList,
      basicComponents =
        if (componentDefinitionExtractionMode.extractBasicDefinitions)
          Some(
            extractComponentDefinitions(uiConfig = ComponentsUiConfig.Empty, configFromProvider = Map.empty).toList
          )
        else
          None
    )
  }

  def fold(
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode,
      components: List[Components]
  ): Components = {
    validated(components.foldLeft(Components.empty(componentDefinitionExtractionMode))(Components.combine))
  }

  private def validated(components: Components): Components = {
    checkDuplicates(components)
    components
  }

  private def checkDuplicates(components: Components): Unit = {
    def findDuplicates(values: List[ComponentDefinitionWithImplementation]) = {
      values
        .map(component => component.id -> component)
        .toGroupedMap
        .filter(_._2.size > 1)
        .keys
        .toList
        .sorted
    }

    val componentsDuplicates      = findDuplicates(components.components)
    val basicComponentsDuplicates = components.basicComponents.map(findDuplicates).getOrElse(List.empty)

    if (componentsDuplicates.nonEmpty) {
      throw new DuplicatedComponentsException(componentsDuplicates)
    } else if (basicComponentsDuplicates.nonEmpty) {
      throw new DuplicatedComponentsException(basicComponentsDuplicates)
    }
  }

  private def combine(x: Components, y: Components): Components = {
    x.copy(
      components = x.components ::: y.components,
      basicComponents = x.basicComponents.map(components => components ::: y.basicComponents.getOrElse(List.empty)),
    )
  }

}
