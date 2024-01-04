package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentGroupName, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.{
  ComponentStaticDefinition,
  CustomComponentSpecificData,
  FragmentSpecificData
}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.SortedComponentGroup
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.collection.immutable.ListMap

class ComponentGroupsPreparer(componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]]) {

  import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName._

  def prepareComponentGroups(
      user: LoggedUser,
      definitions: ModelDefinition[ComponentStaticDefinition],
      // FIXME: Remove this
      componentsConfig: ComponentsUiConfig,
      processCategoryService: ProcessCategoryService,
      processingType: ProcessingType
  ): List[ComponentGroup] = {
    val userCategoryService          = new UserCategoryService(processCategoryService)
    val userCategories               = userCategoryService.getUserCategories(user)
    val processingTypeCategories     = List(processCategoryService.getProcessingTypeCategoryUnsafe(processingType))
    val userProcessingTypeCategories = userCategories.intersect(processingTypeCategories)

    def filterCategories(componentDefinition: ComponentStaticDefinition): List[String] =
      userProcessingTypeCategories.intersect(
        componentDefinition.categories.getOrElse(processCategoryService.getAllCategories)
      )

    def nodeTemplateParameters(componentDefinition: ComponentStaticDefinition): List[NodeParameter] =
      NodeTemplateParameterPreparer.prepareNodeTemplateParameter(componentDefinition.parameters)

    def nodeTemplateBranchParameters(componentDefinition: ComponentStaticDefinition): List[NodeParameter] =
      NodeTemplateParameterPreparer.prepareNodeTemplateBranchParameter(componentDefinition.parameters)

    def serviceRef(info: ComponentInfo, componentDefinition: ComponentStaticDefinition) =
      ServiceRef(info.name, nodeTemplateParameters(componentDefinition))

    val builtInComponents =
      for {
        (info, componentDefinition) <- definitions.components.toList
        nodeTemplate <- Option(info).collect {
          case BuiltInComponentInfo.Filter   => Filter("", Expression.spel("true"))
          case BuiltInComponentInfo.Split    => Split("")
          case BuiltInComponentInfo.Choice   => Switch("")
          case BuiltInComponentInfo.Variable => Variable("", "varName", Expression.spel("'value'"))
          case BuiltInComponentInfo.RecordVariable =>
            VariableBuilder("", "varName", List(Field("fieldName", Expression.spel("'value'"))))
        }
      } yield ComponentNodeTemplate.create(info, nodeTemplate, filterCategories(componentDefinition))

    val base = ComponentGroup(BaseGroupName, builtInComponents)

    val services = ComponentGroup(
      ServicesGroupName,
      definitions.components.toList.collect {
        case (info, componentDefinition)
            if componentDefinition.componentType == ComponentType.Service && !componentDefinition.hasReturn =>
          ComponentNodeTemplate(
            ComponentType.Service,
            info.name,
            Processor("", serviceRef(info, componentDefinition)),
            filterCategories(componentDefinition)
          )
      }
    )

    val enrichers = ComponentGroup(
      EnrichersGroupName,
      definitions.components.toList.collect {
        case (info, componentDefinition)
            if componentDefinition.componentType == ComponentType.Service && componentDefinition.hasReturn =>
          ComponentNodeTemplate(
            ComponentType.Service,
            info.name,
            Enricher("", serviceRef(info, componentDefinition), "output"),
            filterCategories(componentDefinition),
            isEnricher = Some(true)
          )
      }
    )

    val customTransformers = ComponentGroup(
      CustomGroupName,
      definitions.components.toList.collect {
        // branchParameters = List.empty can be tricky here. We moved template for branch parameters to NodeToAdd because
        // branch parameters inside node.Join are branchId -> List[NodeParameter] and on node template level we don't know what
        // branches will be. After moving this parameters to BranchEnd it will disappear from here.
        // Also it is not the best design pattern to reply with backend's NodeData as a template in API.
        // TODO: keep only custom node ids in componentGroups element and move templates to parameters definition API
        case (
              info,
              componentDefinition @ ComponentStaticDefinition(_, _, _, _, CustomComponentSpecificData(true, _))
            ) =>
          ComponentNodeTemplate(
            ComponentType.CustomComponent,
            info.name,
            node.Join(
              "",
              if (componentDefinition.hasReturn) Some("outputVar") else None,
              info.name,
              nodeTemplateParameters(componentDefinition),
              List.empty
            ),
            filterCategories(componentDefinition),
            nodeTemplateBranchParameters(componentDefinition)
          )
        case (
              info,
              componentDefinition @ ComponentStaticDefinition(_, _, _, _, CustomComponentSpecificData(false, false))
            ) =>
          ComponentNodeTemplate(
            ComponentType.CustomComponent,
            info.name,
            CustomNode(
              "",
              if (componentDefinition.hasReturn) Some("outputVar") else None,
              info.name,
              nodeTemplateParameters(componentDefinition)
            ),
            filterCategories(componentDefinition)
          )
      }
    )

    val optionalEndingCustomTransformers = ComponentGroup(
      OptionalEndingCustomGroupName,
      definitions.components.toList.collect {
        case (
              info,
              componentDefinition @ ComponentStaticDefinition(_, _, _, _, CustomComponentSpecificData(false, true))
            ) =>
          ComponentNodeTemplate(
            ComponentType.CustomComponent,
            info.name,
            CustomNode(
              "",
              if (componentDefinition.hasReturn) Some("outputVar") else None,
              info.name,
              nodeTemplateParameters(componentDefinition)
            ),
            filterCategories(componentDefinition)
          )
      }
    )

    val sinks = ComponentGroup(
      SinksGroupName,
      definitions.components.toList.collect {
        case (info, componentDefinition) if componentDefinition.componentType == ComponentType.Sink =>
          ComponentNodeTemplate(
            ComponentType.Sink,
            info.name,
            Sink("", SinkRef(info.name, nodeTemplateParameters(componentDefinition))),
            filterCategories(componentDefinition)
          )
      }
    )

    val sources = ComponentGroup(
      SourcesGroupName,
      definitions.components.toList.collect {
        case (info, componentDefinition) if componentDefinition.componentType == ComponentType.Source =>
          ComponentNodeTemplate(
            ComponentType.Source,
            info.name,
            Source("", SourceRef(info.name, nodeTemplateParameters(componentDefinition))),
            filterCategories(componentDefinition)
          )
      }
    )

    val fragmentDefinitions = {
      val fragmentDefinitionComponents =
        for {
          (info, componentDefinition) <- definitions.components.toList
          nodeTemplate <- Option(info).collect {
            case BuiltInComponentInfo.FragmentInputDefinition  => FragmentInputDefinition("", List.empty)
            case BuiltInComponentInfo.FragmentOutputDefinition => FragmentOutputDefinition("", "output", List.empty)
          }
        } yield ComponentNodeTemplate.create(info, nodeTemplate, filterCategories(componentDefinition))
      ComponentGroup(FragmentsDefinitionGroupName, fragmentDefinitionComponents)
    }

    val fragments =
      ComponentGroup(
        FragmentsGroupName,
        definitions.components.toList.collect {
          case (
                info,
                componentDefinition @ ComponentStaticDefinition(_, _, _, _, FragmentSpecificData(outputNames))
              ) =>
            val nodes      = NodeTemplateParameterPreparer.prepareNodeTemplateParameter(componentDefinition.parameters)
            val outputs    = outputNames.map(name => (name, name)).toMap
            val categories = filterCategories(componentDefinition)
            ComponentNodeTemplate(
              ComponentType.Fragment,
              info.name,
              FragmentInput("", FragmentRef(info.name, nodes, outputs)),
              categories
            )
        }
      )

    // return none if component group should be hidden
    def getComponentGroupName(
        componentName: String,
        baseComponentGroupName: ComponentGroupName
    ): Option[ComponentGroupName] = {
      val groupName =
        componentsConfig.getConfigByComponentName(componentName).componentGroup.getOrElse(baseComponentGroupName)
      componentsGroupMapping.getOrElse(groupName, Some(groupName))
    }

    val virtualComponentGroups = List(
      List(sources, fragmentDefinitions),
      List(base),
      List(enrichers, customTransformers, fragments),
      List(services, optionalEndingCustomTransformers, sinks)
    )

    virtualComponentGroups.zipWithIndex
      .flatMap { case (groups, virtualGroupIndex) =>
        for {
          group                   <- groups
          component               <- group.components
          notHiddenComponentGroup <- getComponentGroupName(component.label, group.name)
        } yield (virtualGroupIndex, notHiddenComponentGroup, component)
      }
      .groupBy { case (virtualGroupIndex, componentGroupName, _) =>
        (virtualGroupIndex, componentGroupName)
      }
      .mapValuesNow(v => v.map(e => e._3))
      .toList
      .sortBy { case ((virtualGroupIndex, componentGroupName), _) =>
        (virtualGroupIndex, componentGroupName.toLowerCase)
      }
      // we need to merge nodes in the same category but in other virtual group
      .foldLeft(ListMap.empty[ComponentGroupName, List[ComponentNodeTemplate]]) {
        case (acc, ((_, componentGroupName), elements)) =>
          val accElements = acc.getOrElse(componentGroupName, List.empty) ++ elements
          acc + (componentGroupName -> accElements)
      }
      .toList
      .map { case (componentGroupName, elements: List[ComponentNodeTemplate]) =>
        SortedComponentGroup(componentGroupName, elements)
      }
  }

}
