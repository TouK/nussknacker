package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.DefinitionsService.{createUIParameter, createUIScenarioPropertyConfig}
import pl.touk.nussknacker.ui.definition.component.{ComponentGroupsPreparer, ComponentWithStaticDefinition}
import pl.touk.nussknacker.ui.definition.scenarioproperty.{FragmentPropertiesConfig, UiScenarioPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// This class only combines various views on definitions for the FE. It is executed for each request, when user
// enters the scenario view. The core domain logic should be done during Model definition extraction
class DefinitionsService(
    modelData: ModelData,
    staticDefinitionForDynamicComponents: Map[ComponentId, ComponentStaticDefinition],
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    fragmentPropertiesConfig: Map[String, ScenarioPropertyConfig],
    deploymentManager: DeploymentManager,
    alignedComponentsDefinitionProvider: AlignedComponentsDefinitionProvider,
    scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
    fragmentRepository: FragmentRepository
)(implicit ec: ExecutionContext) {

  def prepareUIDefinitions(processingType: ProcessingType, forFragment: Boolean)(
      implicit user: LoggedUser
  ): Future[UIDefinitions] = {
    fragmentRepository.fetchLatestFragments(processingType).map { fragments =>
      val alignedComponentsDefinition =
        alignedComponentsDefinitionProvider
          .getAlignedComponentsWithBuiltInComponentsAndFragments(forFragment, fragments)

      val withStaticDefinition = alignedComponentsDefinition.map {
        case dynamic: DynamicComponentDefinitionWithImplementation =>
          val staticDefinition = staticDefinitionForDynamicComponents.getOrElse(
            dynamic.id,
            throw new IllegalStateException(s"Static definition for dynamic component: $dynamic should be precomputed")
          )
          ComponentWithStaticDefinition(dynamic, staticDefinition)
        case methodBased: MethodBasedComponentDefinitionWithImplementation =>
          ComponentWithStaticDefinition(methodBased, methodBased.staticDefinition)
        case other =>
          throw new IllegalStateException(s"Unknown component representation: $other")
      }

      val finalizedScenarioPropertiesConfig = scenarioPropertiesConfigFinalizer
        .finalizeScenarioProperties(scenarioPropertiesConfig)

      prepareUIDefinitions(
        withStaticDefinition,
        forFragment,
        finalizedScenarioPropertiesConfig
      )
    }
  }

  private def prepareUIDefinitions(
      components: List[ComponentWithStaticDefinition],
      forFragment: Boolean,
      finalizedScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig]
  ): UIDefinitions = {
    UIDefinitions(
      componentGroups = ComponentGroupsPreparer.prepareComponentGroups(components),
      components = components.map(component => component.component.id -> createUIComponentDefinition(component)).toMap,
      classes = modelData.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      scenarioPropertiesConfig = (if (forFragment) FragmentPropertiesConfig.properties ++ fragmentPropertiesConfig
                                  else finalizedScenarioPropertiesConfig)
        .mapValuesNow(createUIScenarioPropertyConfig),
      edgesForNodes = EdgeTypesPreparer.prepareEdgeTypes(components.map(_.component)),
      customActions = deploymentManager.customActionsDefinitions.map(UICustomAction(_))
    )
  }

  private def createUIComponentDefinition(
      componentDefinition: ComponentWithStaticDefinition
  ): UIComponentDefinition = {
    UIComponentDefinition(
      parameters = componentDefinition.staticDefinition.parameters.map(createUIParameter),
      returnType = componentDefinition.staticDefinition.returnType,
      icon = componentDefinition.component.icon,
      docsUrl = componentDefinition.component.docsUrl,
      outputParameters = Option(componentDefinition.component.componentTypeSpecificData).collect {
        case FragmentSpecificData(outputNames) => outputNames
      }
    )
  }

}

object DefinitionsService {

  def apply(
      processingTypeData: ProcessingTypeData,
      alignedComponentsDefinitionProvider: AlignedComponentsDefinitionProvider,
      scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
      fragmentRepository: FragmentRepository
  )(implicit ec: ExecutionContext) =
    new DefinitionsService(
      processingTypeData.designerModelData.modelData,
      processingTypeData.designerModelData.staticDefinitionForDynamicComponents,
      processingTypeData.deploymentData.scenarioPropertiesConfig,
      processingTypeData.deploymentData.fragmentPropertiesConfig,
      processingTypeData.deploymentData.validDeploymentManagerOrStub,
      alignedComponentsDefinitionProvider,
      scenarioPropertiesConfigFinalizer,
      fragmentRepository
    )

  def createUIParameter(parameter: Parameter): UIParameter = {
    UIParameter(
      name = parameter.name.value,
      typ = parameter.typ,
      editor = parameter.finalEditor,
      defaultValue = parameter.finalDefaultValue,
      additionalVariables = parameter.additionalVariables.mapValuesNow(_.typingResult),
      variablesToHide = parameter.variablesToHide,
      branchParam = parameter.branchParam,
      hintText = parameter.hintText,
      label = parameter.label
    )
  }

  def createUIScenarioPropertyConfig(config: ScenarioPropertyConfig): UiScenarioPropertiesParameterConfig = {
    val editor = UiScenarioPropertyEditorDeterminer.determine(config)
    UiScenarioPropertiesParameterConfig(config.defaultValue, editor, config.label, config.hintText)
  }

}
