package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.restmodel.definition.{ComponentTemplate, UIProcessObjects}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

private[component] case class ComponentObjects(templates: List[(ComponentGroupName, ComponentTemplate)],
                                               config: ComponentsUiConfig)

private[component] object ComponentObjects {

  def apply(uIProcessObjects: UIProcessObjects): ComponentObjects = {
    val templates = uIProcessObjects.componentGroups.flatMap(group => group.components.map(component => (group.name, component)))
    ComponentObjects(templates, uIProcessObjects.componentsConfig)
  }

}

/**
 * TODO: Right now we use UIProcessObjectsFactory for extract components data, because there is assigned logic
 * responsible for: hiding, mapping group name, etc.. We should move this logic to another place, because
 * UIProcessObjectsFactory does many other things, things that we don't need here..
 */
private[component] class ComponentObjectsService(categoryService: ProcessCategoryService) {

  def prepareWithoutFragments(processingType: ProcessingType, processingTypeData: ProcessingTypeData): ComponentObjects = {
    val uiProcessObjects = createUIProcessObjects(
      processingType,
      processingTypeData,
      user = NussknackerInternalUser, // We need admin user to receive all components info
      fragments = Set.empty,
    )
    ComponentObjects(uiProcessObjects)
  }

  def prepare(processingType: ProcessingType,
              processingTypeData: ProcessingTypeData,
              user: LoggedUser,
              fragments: Set[FragmentDetails]): ComponentObjects = {
    val uiProcessObjects = createUIProcessObjects(processingType, processingTypeData, user, fragments)
    ComponentObjects(uiProcessObjects)
  }

  private def createUIProcessObjects(processingType: ProcessingType,
                                     processingTypeData: ProcessingTypeData,
                                     user: LoggedUser,
                                     fragments: Set[FragmentDetails]): UIProcessObjects = {
    UIProcessObjectsFactory.prepareUIProcessObjects(
      modelDataForType = processingTypeData.modelData,
      deploymentManager = processingTypeData.deploymentManager,
      typeSpecificInitialData = processingTypeData.typeSpecificInitialData,
      user = user,
      fragmentsDetails = fragments,
      isFragment = false, //It excludes fragment's components: input / output
      processCategoryService = categoryService,
      additionalPropertiesConfig = processingTypeData.additionalPropertiesConfig,
      processingType = processingType
    )
  }

}
