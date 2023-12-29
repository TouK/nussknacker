package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{
  BuiltInComponentInfo,
  ComponentGroupName,
  ComponentType,
  SingleComponentConfig
}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.fragment.FragmentStaticDefinition
import pl.touk.nussknacker.engine.definition.model.{ComponentIdWithName, ModelDefinitionWithComponentIds}
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.graph.{EdgeType, node}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.SortedComponentGroup
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.collection.immutable.ListMap

//TODO: some refactoring?
object ComponentDefinitionPreparer {

  import DefaultsComponentGroupName._
  import cats.instances.map._
  import cats.syntax.semigroup._

  def prepareComponentsGroupList(
      user: LoggedUser,
      modelDefinition: ModelDefinitionWithComponentIds[ComponentStaticDefinition],
      fragmentComponents: Map[String, FragmentStaticDefinition],
      isFragment: Boolean,
      componentsConfig: ComponentsUiConfig,
      componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
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

    def serviceRef(idWithName: ComponentIdWithName, componentDefinition: ComponentStaticDefinition) =
      ServiceRef(idWithName.name, nodeTemplateParameters(componentDefinition))

    val hasNoReturn =
      (
          (_: ComponentIdWithName, componentDefinition: ComponentStaticDefinition) => componentDefinition.hasNoReturn
      ).tupled

    // TODO: make it possible to configure other defaults here.
    val base = ComponentGroup(
      BaseGroupName,
      List(
        ComponentNodeTemplate
          .create(BuiltInComponentInfo.Filter, Filter("", Expression.spel("true")), userProcessingTypeCategories),
        ComponentNodeTemplate.create(BuiltInComponentInfo.Split, Split(""), userProcessingTypeCategories),
        ComponentNodeTemplate.create(BuiltInComponentInfo.Choice, Switch(""), userProcessingTypeCategories),
        ComponentNodeTemplate.create(
          BuiltInComponentInfo.Variable,
          Variable("", "varName", Expression.spel("'value'")),
          userProcessingTypeCategories
        ),
        ComponentNodeTemplate.create(
          BuiltInComponentInfo.RecordVariable,
          VariableBuilder("", "varName", List(Field("fieldName", Expression.spel("'value'")))),
          userProcessingTypeCategories
        ),
      )
    )

    val services = ComponentGroup(
      ServicesGroupName,
      modelDefinition.components
        .filter(_._2.componentType == ComponentType.Service)
        .filter(hasNoReturn)
        .map { case (idWithName, componentDefinition) =>
          ComponentNodeTemplate(
            ComponentType.Service,
            idWithName.name,
            Processor("", serviceRef(idWithName, componentDefinition)),
            filterCategories(componentDefinition)
          )
        }
    )

    val enrichers = ComponentGroup(
      EnrichersGroupName,
      modelDefinition.components
        .filter(_._2.componentType == ComponentType.Service)
        .filterNot(hasNoReturn)
        .map { case (idWithName, componentDefinition) =>
          ComponentNodeTemplate(
            ComponentType.Service,
            idWithName.name,
            Enricher("", serviceRef(idWithName, componentDefinition), "output"),
            filterCategories(componentDefinition),
            isEnricher = Some(true)
          )
        }
    )

    val customTransformers = ComponentGroup(
      CustomGroupName,
      modelDefinition.components
        .collect {
          // branchParameters = List.empty can be tricky here. We moved template for branch parameters to NodeToAdd because
          // branch parameters inside node.Join are branchId -> List[NodeParameter] and on node template level we don't know what
          // branches will be. After moving this parameters to BranchEnd it will disappear from here.
          // Also it is not the best design pattern to reply with backend's NodeData as a template in API.
          // TODO: keep only custom node ids in componentGroups element and move templates to parameters definition API
          case (idWithName, componentDefinition)
              if componentDefinition.componentType == ComponentType.CustomComponent &&
                componentDefinition.componentTypeSpecificData.asCustomComponentData.manyInputs =>
            ComponentNodeTemplate(
              ComponentType.CustomComponent,
              idWithName.name,
              node.Join(
                "",
                if (componentDefinition.hasNoReturn) None else Some("outputVar"),
                idWithName.name,
                nodeTemplateParameters(componentDefinition),
                List.empty
              ),
              filterCategories(componentDefinition),
              nodeTemplateBranchParameters(componentDefinition)
            )
          case (idWithName, componentDefinition)
              if componentDefinition.componentType == ComponentType.CustomComponent &&
                !componentDefinition.componentTypeSpecificData.asCustomComponentData.canBeEnding =>
            ComponentNodeTemplate(
              ComponentType.CustomComponent,
              idWithName.name,
              CustomNode(
                "",
                if (componentDefinition.hasNoReturn) None else Some("outputVar"),
                idWithName.name,
                nodeTemplateParameters(componentDefinition)
              ),
              filterCategories(componentDefinition)
            )
        }
    )

    val optionalEndingCustomTransformers = ComponentGroup(
      OptionalEndingCustomGroupName,
      modelDefinition.components.collect {
        case (idWithName, componentDefinition)
            if componentDefinition.componentType == ComponentType.CustomComponent &&
              componentDefinition.componentTypeSpecificData.asCustomComponentData.canBeEnding =>
          ComponentNodeTemplate(
            ComponentType.CustomComponent,
            idWithName.name,
            CustomNode(
              "",
              if (componentDefinition.hasNoReturn) None else Some("outputVar"),
              idWithName.name,
              nodeTemplateParameters(componentDefinition)
            ),
            filterCategories(componentDefinition)
          )
      }
    )

    val sinks = ComponentGroup(
      SinksGroupName,
      modelDefinition.components.collect {
        case (idWithName, componentDefinition) if componentDefinition.componentType == ComponentType.Sink =>
          ComponentNodeTemplate(
            ComponentType.Sink,
            idWithName.name,
            Sink("", SinkRef(idWithName.name, nodeTemplateParameters(componentDefinition))),
            filterCategories(componentDefinition)
          )
      }
    )

    val inputs = if (!isFragment) {
      ComponentGroup(
        SourcesGroupName,
        modelDefinition.components.collect {
          case (idWithName, componentDefinition) if componentDefinition.componentType == ComponentType.Source =>
            ComponentNodeTemplate(
              ComponentType.Source,
              idWithName.name,
              Source("", SourceRef(idWithName.name, nodeTemplateParameters(componentDefinition))),
              filterCategories(componentDefinition)
            )
        }
      )
    } else {
      ComponentGroup(
        FragmentsDefinitionGroupName,
        List(
          ComponentNodeTemplate.create(
            BuiltInComponentInfo.FragmentInputDefinition,
            FragmentInputDefinition("", List()),
            userProcessingTypeCategories
          ),
          ComponentNodeTemplate.create(
            BuiltInComponentInfo.FragmentOutputDefinition,
            FragmentOutputDefinition("", "output", List.empty),
            userProcessingTypeCategories
          )
        )
      )
    }

    // so far we don't allow nested fragments...
    val fragments = if (!isFragment) {
      List(
        ComponentGroup(
          FragmentsGroupName,
          fragmentComponents.map { case (id, definition) =>
            val nodes =
              NodeTemplateParameterPreparer.prepareNodeTemplateParameter(definition.componentDefinition.parameters)
            val outputs = definition.outputNames.map(name => (name, name)).toMap
            val categories = userProcessingTypeCategories.intersect(
              definition.componentDefinition.categories.getOrElse(processCategoryService.getAllCategories)
            )
            ComponentNodeTemplate(
              ComponentType.Fragment,
              id,
              FragmentInput("", FragmentRef(id, nodes, outputs)),
              categories
            )
          }.toList
        )
      )
    } else {
      List.empty
    }

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
      List(inputs),
      List(base),
      List(enrichers, customTransformers) ++ fragments,
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

  def prepareEdgeTypes(
      modelDefinition: ModelDefinitionWithComponentIds[ComponentStaticDefinition],
      isFragment: Boolean,
      fragmentsDetails: Set[FragmentDetails]
  ): List[NodeEdges] = {

    val fragmentOutputs =
      if (isFragment) List()
      else
        fragmentsDetails.map(_.canonical).map { process =>
          val outputs = ProcessConverter.findNodes(process).collect { case FragmentOutputDefinition(_, name, _, _) =>
            name
          }
          // TODO: enable choice of output type
          NodeEdges(
            NodeTypeId("FragmentInput", Some(process.metaData.id)),
            outputs.map(EdgeType.FragmentOutput),
            canChooseNodes = false,
            isForInputDefinition = false
          )
        }

    val joinInputs = modelDefinition.components.collect {
      case (idWithName, component)
          if component.componentType == ComponentType.CustomComponent &&
            component.componentTypeSpecificData.asCustomComponentData.manyInputs =>
        NodeEdges(NodeTypeId("Join", Some(idWithName.name)), List(), canChooseNodes = true, isForInputDefinition = true)
    }

    List(
      NodeEdges(
        NodeTypeId("Split", Some(BuiltInComponentInfo.Split.name)),
        List(),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      NodeEdges(
        NodeTypeId("Switch", Some(BuiltInComponentInfo.Choice.name)),
        List(EdgeType.NextSwitch(Expression.spel("true")), EdgeType.SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      NodeEdges(
        NodeTypeId("Filter", Some(BuiltInComponentInfo.Filter.name)),
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      )
    ) ++ fragmentOutputs ++ joinInputs
  }

  def combineComponentsConfig(configs: ComponentsUiConfig*): ComponentsUiConfig = configs.reduce(_ |+| _)
}
