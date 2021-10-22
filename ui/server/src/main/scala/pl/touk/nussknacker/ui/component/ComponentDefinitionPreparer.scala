package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.restmodel.component.ComponentType
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.ui.definition.{EvaluatedParameterPreparer, SortedComponentGroup}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission.Read

import scala.collection.immutable.ListMap

//TODO: some refactoring?
object ComponentDefinitionPreparer {

  import cats.instances.map._
  import cats.syntax.semigroup._
  import DefaultsComponentGroupName._

  def prepareComponentsGroupList(user: LoggedUser,
                                 processDefinition: UIProcessDefinition,
                                 isSubprocess: Boolean,
                                 componentsConfig: ComponentsUiConfig,
                                 componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
                                 processCategoryService: ProcessCategoryService,
                                 customTransformerAdditionalData: Map[String, CustomTransformerAdditionalData]
                                ): List[ComponentGroup] = {
    val readCategories = processCategoryService.getUserCategories(user)

    def filterCategories(objectDefinition: UIObjectDefinition): List[String] = readCategories.intersect(objectDefinition.categories)

    def objDefParams(id: String, objDefinition: UIObjectDefinition): List[Parameter] =
      EvaluatedParameterPreparer.prepareEvaluatedParameter(objDefinition.parameters)

    def objDefBranchParams(id: String, objDefinition: UIObjectDefinition): List[Parameter] =
      EvaluatedParameterPreparer.prepareEvaluatedBranchParameter(objDefinition.parameters)

    def serviceRef(id: String, objDefinition: UIObjectDefinition) = ServiceRef(id, objDefParams(id, objDefinition))

    val returnsUnit = ((_: String, objectDefinition: UIObjectDefinition) => objectDefinition.hasNoReturn).tupled

    //TODO: make it possible to configure other defaults here.
    val base = ComponentGroup(BaseGroupName, List(
      ComponentTemplate.create(ComponentType.Filter, Filter("", Expression("spel", "true")), readCategories),
      ComponentTemplate.create(ComponentType.Split, Split(""), readCategories),
      ComponentTemplate.create(ComponentType.Switch, Switch("", Expression("spel", "true"), "output"), readCategories),
      ComponentTemplate.create(ComponentType.Variable, Variable("", "varName", Expression("spel", "'value'")), readCategories),
      ComponentTemplate.create(ComponentType.MapVariable, VariableBuilder("", "mapVarName", List(Field("varName", Expression("spel", "'value'")))), readCategories),
    ))

    val services = ComponentGroup(ServicesGroupName,
      processDefinition.services.filter(returnsUnit).map {
        case (id, objDefinition) => ComponentTemplate(ComponentType.Processor, id,
          Processor("", serviceRef(id, objDefinition)), filterCategories(objDefinition))
      }.toList
    )

    val enrichers = ComponentGroup(EnrichersGroupName,
      processDefinition.services.filterNot(returnsUnit).map {
        case (id, objDefinition) => ComponentTemplate(ComponentType.Enricher, id,
          Enricher("", serviceRef(id, objDefinition), "output"), filterCategories(objDefinition))
      }.toList
    )

    val customTransformers = ComponentGroup(CustomGroupName,
      processDefinition.customStreamTransformers.collect {
        // branchParameters = List.empty can be tricky here. We moved template for branch parameters to NodeToAdd because
        // branch parameters inside node.Join are branchId -> List[Parameter] and on node template level we don't know what
        // branches will be. After moving this parameters to BranchEnd it will disappear from here.
        // Also it is not the best design pattern to reply with backend's NodeData as a template in API.
        // TODO: keep only custom node ids in componentGroups element and move templates to parameters definition API
        case (id, uiObjectDefinition) if customTransformerAdditionalData(id).manyInputs => ComponentTemplate(ComponentType.CustomNode, id,
          node.Join("", if (uiObjectDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, uiObjectDefinition), List.empty),
          filterCategories(uiObjectDefinition), objDefBranchParams(id, uiObjectDefinition))
        case (id, uiObjectDefinition) if !customTransformerAdditionalData(id).canBeEnding => ComponentTemplate(ComponentType.CustomNode, id,
          CustomNode("", if (uiObjectDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, uiObjectDefinition)), filterCategories(uiObjectDefinition))
      }.toList
    )

    val optionalEndingCustomTransformers = ComponentGroup(OptionalEndingCustomGroupName,
      processDefinition.customStreamTransformers.collect {
        case (id, uiObjectDefinition) if customTransformerAdditionalData(id).canBeEnding => ComponentTemplate(ComponentType.CustomNode, id,
          CustomNode("", if (uiObjectDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, uiObjectDefinition)), filterCategories(uiObjectDefinition))
      }.toList
    )

    val sinks = ComponentGroup(SinksGroupName,
      processDefinition.sinkFactories.map {
        case (id, uiObjectDefinition) => ComponentTemplate(ComponentType.Sink, id,
          Sink("", SinkRef(id, objDefParams(id, uiObjectDefinition))), filterCategories(uiObjectDefinition)
        )
      }.toList)

    val inputs = if (!isSubprocess) {
      ComponentGroup(SourcesGroupName,
        processDefinition.sourceFactories.map {
          case (id, objDefinition) => ComponentTemplate(ComponentType.Source, id,
            Source("", SourceRef(id, objDefParams(id, objDefinition))),
            filterCategories(objDefinition)
          )
        }.toList)
    } else {
      ComponentGroup(FragmentsDefinitionGroupName, List(
        ComponentTemplate.create(ComponentType.FragmentInput, SubprocessInputDefinition("", List()), readCategories),
        ComponentTemplate.create(ComponentType.FragmentOutput, SubprocessOutputDefinition("", "output", List.empty), readCategories)
      ))
    }

    //so far we don't allow nested subprocesses...
    val subprocesses = if (!isSubprocess) {
      List(
        ComponentGroup(FragmentsGroupName,
          processDefinition.subprocessInputs.map {
            case (id, definition) =>
              val nodes = EvaluatedParameterPreparer.prepareEvaluatedParameter(definition.parameters)
              ComponentTemplate(ComponentType.Fragments, id, SubprocessInput("", SubprocessRef(id, nodes)), readCategories.intersect(definition.categories))
          }.toList))
    } else {
      List.empty
    }

    // return none if component group should be hidden
    def getComponentGroupName(componentName: String, baseComponentGroupName: ComponentGroupName): Option[ComponentGroupName] = {
      val groupName = componentsConfig.get(componentName).flatMap(_.componentGroup).getOrElse(baseComponentGroupName)
      componentsGroupMapping.getOrElse(groupName, Some(groupName))
    }

    val virtualComponentGroups = List(
      List(inputs),
      List(base),
      List(enrichers, customTransformers) ++ subprocesses,
      List(services, optionalEndingCustomTransformers, sinks)
    )

    virtualComponentGroups
      .zipWithIndex
      .flatMap {
        case (groups, virtualGroupIndex) =>
          for {
            group <- groups
            component <- group.components
            notHiddenComponentGroup <- getComponentGroupName(component.label, group.name)
          } yield (virtualGroupIndex, notHiddenComponentGroup, component)
      }
      .groupBy {
        case (virtualGroupIndex, componentGroupName, _) => (virtualGroupIndex, componentGroupName)
      }
      .mapValues(v => v.map(e => e._3))
      .toList
      .sortBy {
        case ((virtualGroupIndex, componentGroupName), _) => (virtualGroupIndex, componentGroupName.toLowerCase)
      }
      // we need to merge nodes in the same category but in other virtual group
      .foldLeft(ListMap.empty[ComponentGroupName, List[ComponentTemplate]]) {
        case (acc, ((_, componentGroupName), elements)) =>
          val accElements = acc.getOrElse(componentGroupName, List.empty) ++ elements
          acc + (componentGroupName -> accElements)
      }
      .toList
      .map {
        case (componentGroupName, elements: List[ComponentTemplate]) => SortedComponentGroup(componentGroupName, elements)
      }
  }

  def prepareEdgeTypes(processDefinition: ProcessDefinition[ObjectDefinition],
                       isSubprocess: Boolean,
                       subprocessesDetails: Set[SubprocessDetails]): List[NodeEdges] = {

    val subprocessOutputs = if (isSubprocess) List() else subprocessesDetails.map(_.canonical).map { process =>
      val outputs = ProcessConverter.findNodes(process).collect {
        case SubprocessOutputDefinition(_, name, _, _) => name
      }
      //TODO: enable choice of output type
      NodeEdges(NodeTypeId("SubprocessInput", Some(process.metaData.id)), outputs.map(EdgeType.SubprocessOutput),
        canChooseNodes = false, isForInputDefinition = false)
    }

    val joinInputs = processDefinition.customStreamTransformers.collect {
      case (name, value) if value._2.manyInputs =>
        NodeEdges(NodeTypeId("Join", Some(name)), List(), canChooseNodes = true, isForInputDefinition = true)
    }

    List(
      NodeEdges(NodeTypeId("Split"), List(), canChooseNodes = true, isForInputDefinition = false),
      NodeEdges(NodeTypeId("Switch"), List(
        EdgeType.NextSwitch(Expression("spel", "true")), EdgeType.SwitchDefault), canChooseNodes = true, isForInputDefinition = false),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), canChooseNodes = false, isForInputDefinition = false)
    ) ++ subprocessOutputs ++ joinInputs
  }

  def combineComponentsConfig(configs: ComponentsUiConfig*): ComponentsUiConfig = configs.reduce(_ |+| _)
}
