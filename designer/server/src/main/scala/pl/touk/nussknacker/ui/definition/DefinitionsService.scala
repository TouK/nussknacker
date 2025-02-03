package pl.touk.nussknacker.ui.definition

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{Components, FragmentSpecificData}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.DefinitionsService.{
  ComponentUiConfigMode,
  createUIParameter,
  createUIScenarioPropertyConfig
}
import pl.touk.nussknacker.ui.definition.component.{ComponentGroupsPreparer, ComponentWithStaticDefinition}
import pl.touk.nussknacker.ui.definition.scenarioproperty.{FragmentPropertiesConfig, UiScenarioPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtype.{DesignerModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// This class only combines various views on definitions for the FE. It is executed for each request, when user
// enters the scenario view. The core domain logic should be done during Model definition extraction
class DefinitionsService(
    modelData: ModelData,
    staticDefinitionForDynamicComponents: DesignerModelData.DynamicComponentsStaticDefinitions,
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    fragmentPropertiesConfig: Map[String, ScenarioPropertyConfig],
    alignedComponentsDefinitionProvider: AlignedComponentsDefinitionProvider,
    scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
    fragmentRepository: FragmentRepository,
    fragmentPropertiesDocsUrl: Option[String],
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  private val fragmentComponentsCache: Cache[(ProcessingType, Boolean), (List[CanonicalProcess], Components)] =
    Caffeine
      .newBuilder()
      .expireAfterAccess(java.time.Duration.ofDays(1))
      .build[(ProcessingType, Boolean), (List[CanonicalProcess], Components)]

  def prepareUIDefinitions(
      processingType: ProcessingType,
      forFragment: Boolean,
      componentUiConfigMode: ComponentUiConfigMode
  )(
      implicit user: LoggedUser
  ): Future[UIDefinitions] = {
    fragmentRepository.fetchLatestFragments(processingType).map { fragments =>
      val alignedComponentsDefinition = getComponents(processingType, forFragment, fragments)
      val withStaticDefinition = {
        val (components, cachedStaticDefinitionsForDynamicComponents) = componentUiConfigMode match {
          case ComponentUiConfigMode.EnrichedWithUiConfig =>
            (alignedComponentsDefinition.components, staticDefinitionForDynamicComponents.finalDefinitions)
          case ComponentUiConfigMode.BasicConfig =>
            (
              alignedComponentsDefinition.basicComponentsUnsafe,
              staticDefinitionForDynamicComponents.basicDefinitionsUnsafe
            )
        }

        components.map {
          case dynamic: DynamicComponentDefinitionWithImplementation =>
            val staticDefinition = cachedStaticDefinitionsForDynamicComponents.getOrElse(
              dynamic.id,
              throw new IllegalStateException(
                s"Static definition for dynamic component: $dynamic should be precomputed"
              )
            )
            ComponentWithStaticDefinition(dynamic, staticDefinition)
          case methodBased: MethodBasedComponentDefinitionWithImplementation =>
            ComponentWithStaticDefinition(methodBased, methodBased.staticDefinition)
          case other =>
            throw new IllegalStateException(s"Unknown component representation: $other")
        }
      }

      val finalizedScenarioPropertiesConfig = componentUiConfigMode match {
        case ComponentUiConfigMode.EnrichedWithUiConfig =>
          scenarioPropertiesConfigFinalizer.finalizeScenarioProperties(scenarioPropertiesConfig)
        case ComponentUiConfigMode.BasicConfig =>
          scenarioPropertiesConfig
      }

      import net.ceedubs.ficus.Ficus._
      val scenarioPropertiesDocsUrl = modelData.modelConfig.getAs[String]("scenarioPropertiesDocsUrl")

      prepareUIDefinitions(
        withStaticDefinition,
        forFragment,
        finalizedScenarioPropertiesConfig,
        scenarioPropertiesDocsUrl
      )
    }
  }

  // Components are fetched from cache, as long as the value in cache exists and fragment definitions did not change since last cache update.
  // Otherwise, the cache in updated.
  private def getComponents(
      processingType: ProcessingType,
      forFragment: Boolean,
      fragments: List[CanonicalProcess],
  ): Components = {
    Option(fragmentComponentsCache.getIfPresent((processingType, forFragment))) match {
      case Some((cachedFragments, cachedComponents)) if cachedFragments == fragments =>
        logger.debug(
          s"Up-to-date components present in cache for processingType=$processingType, forFragment=$forFragment"
        )
        cachedComponents
      case cacheContent @ (Some(_) | None) =>
        cacheContent match {
          case Some(_) =>
            logger.debug(
              s"Out-of-date components present in cache for processingType=$processingType, forFragment=$forFragment"
            )
          case None =>
            logger.debug(
              s"Components not present in cache for processingType=$processingType, forFragment=$forFragment"
            )
        }
        val updatedComponents =
          alignedComponentsDefinitionProvider
            .getAlignedComponentsWithBuiltInComponentsAndFragments(forFragment, fragments)
        fragmentComponentsCache.put((processingType, forFragment), (fragments, updatedComponents))
        logger.debug(s"Updated components for processingType=$processingType, forFragment=$forFragment")
        updatedComponents
    }
  }

  private def prepareUIDefinitions(
      components: List[ComponentWithStaticDefinition],
      forFragment: Boolean,
      finalizedScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      scenarioPropertiesDocsUrl: Option[String]
  ): UIDefinitions = {
    UIDefinitions(
      componentGroups = ComponentGroupsPreparer.prepareComponentGroups(components),
      components = components.map(component => component.component.id -> createUIComponentDefinition(component)).toMap,
      classes = modelData.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      scenarioProperties = {
        if (forFragment) {
          createUIProperties(FragmentPropertiesConfig.properties ++ fragmentPropertiesConfig, fragmentPropertiesDocsUrl)
        } else {
          createUIProperties(finalizedScenarioPropertiesConfig, scenarioPropertiesDocsUrl)
        }
      },
      edgesForNodes = EdgeTypesPreparer.prepareEdgeTypes(components.map(_.component)),
    )
  }

  private def createUIProperties(finalizedProperties: Map[String, ScenarioPropertyConfig], docsUrl: Option[String]) = {
    val transformedProps = finalizedProperties.mapValuesNow(createUIScenarioPropertyConfig)
    UiScenarioProperties(propertiesConfig = transformedProps, docsUrl = docsUrl)
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
      fragmentRepository: FragmentRepository,
      fragmentPropertiesDocsUrl: Option[String]
  )(implicit ec: ExecutionContext) =
    new DefinitionsService(
      processingTypeData.designerModelData.modelData,
      processingTypeData.designerModelData.staticDefinitionForDynamicComponents,
      processingTypeData.deploymentData.scenarioPropertiesConfig,
      processingTypeData.deploymentData.fragmentPropertiesConfig,
      alignedComponentsDefinitionProvider,
      scenarioPropertiesConfigFinalizer,
      fragmentRepository,
      fragmentPropertiesDocsUrl
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
      label = parameter.label,
      requiredParam = Some(!parameter.isOptional),
    )
  }

  def createUIScenarioPropertyConfig(config: ScenarioPropertyConfig): UiScenarioPropertyConfig = {
    val editor = UiScenarioPropertyEditorDeterminer.determine(config)
    UiScenarioPropertyConfig(config.defaultValue, editor, config.label, config.hintText)
  }

  sealed trait ComponentUiConfigMode

  object ComponentUiConfigMode {
    case object EnrichedWithUiConfig extends ComponentUiConfigMode
    case object BasicConfig          extends ComponentUiConfigMode
  }

}
