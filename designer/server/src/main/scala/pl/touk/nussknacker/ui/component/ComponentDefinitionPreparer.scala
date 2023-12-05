package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentGroupName, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{
  ComponentIdWithName,
  CustomTransformerAdditionalData,
  ProcessDefinitionWithComponentIds
}
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.graph.{EdgeType, node}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.FragmentObjectDefinition
import pl.touk.nussknacker.ui.definition.{EvaluatedParameterPreparer, SortedComponentGroup}
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
      processDefinition: ProcessDefinitionWithComponentIds[ObjectDefinition],
      fragmentInputs: Map[String, FragmentObjectDefinition],
      isFragment: Boolean,
      componentsConfig: ComponentsUiConfig,
      componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
      processCategoryService: ProcessCategoryService,
      customTransformerAdditionalData: Map[ComponentId, CustomTransformerAdditionalData],
      processingType: ProcessingType
  ): List[ComponentGroup] = {
    val userCategoryService          = new UserCategoryService(processCategoryService)
    val userCategories               = userCategoryService.getUserCategories(user)
    val processingTypeCategories     = List(processCategoryService.getProcessingTypeCategoryUnsafe(processingType))
    val userProcessingTypeCategories = userCategories.intersect(processingTypeCategories)

    def filterCategories(objectDefinition: ObjectDefinition): List[String] = userProcessingTypeCategories.intersect(
      objectDefinition.categories.getOrElse(processCategoryService.getAllCategories)
    )

    def objDefParams(objDefinition: ObjectDefinition): List[Parameter] =
      EvaluatedParameterPreparer.prepareEvaluatedParameter(objDefinition.parameters)

    def objDefBranchParams(objDefinition: ObjectDefinition): List[Parameter] =
      EvaluatedParameterPreparer.prepareEvaluatedBranchParameter(objDefinition.parameters)

    def serviceRef(idWithName: ComponentIdWithName, objDefinition: ObjectDefinition) =
      ServiceRef(idWithName.name, objDefParams(objDefinition))

    val returnsUnit =
      ((_: ComponentIdWithName, objectDefinition: ObjectDefinition) => objectDefinition.hasNoReturn).tupled

    // TODO: make it possible to configure other defaults here.
    val base = ComponentGroup(
      BaseGroupName,
      List(
        ComponentTemplate
          .create(BuiltInComponentInfo.Filter, Filter("", Expression.spel("true")), userProcessingTypeCategories),
        ComponentTemplate.create(BuiltInComponentInfo.Split, Split(""), userProcessingTypeCategories),
        ComponentTemplate.create(BuiltInComponentInfo.Choice, Switch(""), userProcessingTypeCategories),
        ComponentTemplate.create(
          BuiltInComponentInfo.Variable,
          Variable("", "varName", Expression.spel("'value'")),
          userProcessingTypeCategories
        ),
        ComponentTemplate.create(
          BuiltInComponentInfo.RecordVariable,
          VariableBuilder("", "varName", List(Field("fieldName", Expression.spel("'value'")))),
          userProcessingTypeCategories
        ),
      )
    )

    val services = ComponentGroup(
      ServicesGroupName,
      processDefinition.services
        .filter(returnsUnit)
        .map { case (idWithName, objDefinition) =>
          ComponentTemplate(
            ComponentType.Service,
            idWithName.name,
            Processor("", serviceRef(idWithName, objDefinition)),
            filterCategories(objDefinition)
          )
        }
    )

    val enrichers = ComponentGroup(
      EnrichersGroupName,
      processDefinition.services
        .filterNot(returnsUnit)
        .map { case (idWithName, objDefinition) =>
          ComponentTemplate(
            ComponentType.Service,
            idWithName.name,
            Enricher("", serviceRef(idWithName, objDefinition), "output"),
            filterCategories(objDefinition),
            isEnricher = Some(true)
          )
        }
    )

    val customTransformers = ComponentGroup(
      CustomGroupName,
      processDefinition.customStreamTransformers.collect {
        // branchParameters = List.empty can be tricky here. We moved template for branch parameters to NodeToAdd because
        // branch parameters inside node.Join are branchId -> List[Parameter] and on node template level we don't know what
        // branches will be. After moving this parameters to BranchEnd it will disappear from here.
        // Also it is not the best design pattern to reply with backend's NodeData as a template in API.
        // TODO: keep only custom node ids in componentGroups element and move templates to parameters definition API
        case (idWithName, (objectDefinition, _)) if customTransformerAdditionalData(idWithName.id).manyInputs =>
          ComponentTemplate(
            ComponentType.CustomComponent,
            idWithName.name,
            node.Join(
              "",
              if (objectDefinition.hasNoReturn) None else Some("outputVar"),
              idWithName.name,
              objDefParams(objectDefinition),
              List.empty
            ),
            filterCategories(objectDefinition),
            objDefBranchParams(objectDefinition)
          )
        case (idWithName, (objectDefinition, _)) if !customTransformerAdditionalData(idWithName.id).canBeEnding =>
          ComponentTemplate(
            ComponentType.CustomComponent,
            idWithName.name,
            CustomNode(
              "",
              if (objectDefinition.hasNoReturn) None else Some("outputVar"),
              idWithName.name,
              objDefParams(objectDefinition)
            ),
            filterCategories(objectDefinition)
          )
      }
    )

    val optionalEndingCustomTransformers = ComponentGroup(
      OptionalEndingCustomGroupName,
      processDefinition.customStreamTransformers.collect {
        case (idWithName, (objectDefinition, _)) if customTransformerAdditionalData(idWithName.id).canBeEnding =>
          ComponentTemplate(
            ComponentType.CustomComponent,
            idWithName.name,
            CustomNode(
              "",
              if (objectDefinition.hasNoReturn) None else Some("outputVar"),
              idWithName.name,
              objDefParams(objectDefinition)
            ),
            filterCategories(objectDefinition)
          )
      }
    )

    val sinks = ComponentGroup(
      SinksGroupName,
      processDefinition.sinkFactories.map { case (idWithName, objectDefinition) =>
        ComponentTemplate(
          ComponentType.Sink,
          idWithName.name,
          Sink("", SinkRef(idWithName.name, objDefParams(objectDefinition))),
          filterCategories(objectDefinition)
        )
      }
    )

    val inputs = if (!isFragment) {
      ComponentGroup(
        SourcesGroupName,
        processDefinition.sourceFactories.map { case (idWithName, objDefinition) =>
          ComponentTemplate(
            ComponentType.Source,
            idWithName.name,
            Source("", SourceRef(idWithName.name, objDefParams(objDefinition))),
            filterCategories(objDefinition)
          )
        }
      )
    } else {
      ComponentGroup(
        FragmentsDefinitionGroupName,
        List(
          ComponentTemplate.create(
            BuiltInComponentInfo.FragmentInputDefinition,
            FragmentInputDefinition("", List()),
            userProcessingTypeCategories
          ),
          ComponentTemplate.create(
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
          fragmentInputs.map { case (id, definition) =>
            val nodes   = EvaluatedParameterPreparer.prepareEvaluatedParameter(definition.objectDefinition.parameters)
            val outputs = definition.outputsDefinition.map(name => (name, name)).toMap
            val categories = userProcessingTypeCategories.intersect(
              definition.objectDefinition.categories.getOrElse(processCategoryService.getAllCategories)
            )
            ComponentTemplate(
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
      val groupName = componentsConfig.get(componentName).flatMap(_.componentGroup).getOrElse(baseComponentGroupName)
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
      .foldLeft(ListMap.empty[ComponentGroupName, List[ComponentTemplate]]) {
        case (acc, ((_, componentGroupName), elements)) =>
          val accElements = acc.getOrElse(componentGroupName, List.empty) ++ elements
          acc + (componentGroupName -> accElements)
      }
      .toList
      .map { case (componentGroupName, elements: List[ComponentTemplate]) =>
        SortedComponentGroup(componentGroupName, elements)
      }
  }

  def prepareEdgeTypes(
      processDefinition: ProcessDefinitionWithComponentIds[ObjectDefinition],
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

    val joinInputs = processDefinition.customStreamTransformers.collect {
      case (idWithName, value) if value._2.manyInputs =>
        NodeEdges(NodeTypeId("Join", Some(idWithName.name)), List(), canChooseNodes = true, isForInputDefinition = true)
    }

    List(
      NodeEdges(NodeTypeId("Split"), List(), canChooseNodes = true, isForInputDefinition = false),
      NodeEdges(
        NodeTypeId("Switch"),
        List(EdgeType.NextSwitch(Expression.spel("true")), EdgeType.SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      NodeEdges(
        NodeTypeId("Filter"),
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      )
    ) ++ fragmentOutputs ++ joinInputs
  }

  def combineComponentsConfig(configs: ComponentsUiConfig*): ComponentsUiConfig = configs.reduce(_ |+| _)
}
