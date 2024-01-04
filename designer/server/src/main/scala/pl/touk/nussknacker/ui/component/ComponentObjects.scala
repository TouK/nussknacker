package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, ComponentGroupName}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.restmodel.definition.{ComponentNodeTemplate, UIProcessObjects}
import pl.touk.nussknacker.ui.definition.{ModelDefinitionEnricher, UIProcessObjectsFactory}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

private[component] final case class ComponentObjects(
    // TODO: We shouldn't base on ComponentTemplate here - it is final DTO which should be used only for encoding
    templates: List[(ComponentGroupName, ComponentNodeTemplate)],
    config: ComponentsUiConfig
)

private[component] object ComponentObjects {

  def apply(uIProcessObjects: UIProcessObjects): ComponentObjects = {
    val templates =
      uIProcessObjects.componentGroups.flatMap(group => group.components.map(component => (group.name, component)))
    ComponentObjects(templates, new ComponentsUiConfig(uIProcessObjects.componentsConfig))
  }

}

/**
 * TODO: Right now we use UIProcessObjectsFactory for extract components data, because there is assigned logic
 * responsible for: hiding, mapping group name, etc.. We should move this logic to another place, because
 * UIProcessObjectsFactory does many other things, things that we don't need here..
 */
private[component] class ComponentObjectsService(categoryService: ProcessCategoryService) {

  def prepare(
      processingType: ProcessingType,
      processingTypeData: ProcessingTypeData,
      modelDefinitionEnricher: ModelDefinitionEnricher,
      user: LoggedUser,
      fragments: List[FragmentDetails]
  ): ComponentObjects = {
    val uiProcessObjects =
      createUIProcessObjects(processingType, processingTypeData, modelDefinitionEnricher, user, fragments)
    ComponentObjects(uiProcessObjects)
  }

  private def createUIProcessObjects(
      processingType: ProcessingType,
      processingTypeData: ProcessingTypeData,
      modelDefinitionEnricher: ModelDefinitionEnricher,
      user: LoggedUser,
      fragments: List[FragmentDetails],
  ): UIProcessObjects = {
    val enrichedModelDefinition = modelDefinitionEnricher.modelDefinitionWithBuiltInComponentsAndFragments(
      forFragment = false, // It excludes fragment's components: input / output
      fragments,
      processingType
    )
    UIProcessObjectsFactory.prepareUIProcessObjects(
      modelDataForType = processingTypeData.modelData,
      modelDefinitionWithBuiltInComponentsAndFragments = enrichedModelDefinition,
      deploymentManager = processingTypeData.deploymentManager,
      user = user,
      forFragment = false,
      processCategoryService = categoryService,
      scenarioPropertiesConfig = processingTypeData.scenarioPropertiesConfig,
      processingType = processingType,
    )
  }

}
