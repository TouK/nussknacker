package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.CodecJson
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.ParameterTypeMapper
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition, TransformerId}
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.ui.api.DefinitionPreparer.NodeEdges
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessObjectsFinder
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{DefaultValueExtractorChain, ParamDefaultValueConfig, ParameterEvaluatorExtractor}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, PermissionSyntax}
import pl.touk.nussknacker.ui.util.EspPathMatchers

import scala.concurrent.ExecutionContext
import scala.runtime.BoxedUnit
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}

class DefinitionResources(modelData: Map[ProcessingType, ModelData],
                          subprocessRepository: SubprocessRepository)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with EspPathMatchers with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser) : Route = encodeResponse {
    //TODO maybe always return data for all subprocesses versions instead of fetching just one-by-one?
    path("processDefinitionData" / Segment) { (processingType) =>
      parameter('isSubprocess.as[Boolean]) { (isSubprocess) =>
        post {
          entity(as[Map[String, Long]]) { subprocessVersions =>
            complete {
              val response: HttpResponse = modelData.get(processingType).map { modelDataForType =>
                val processConfig = modelDataForType.processConfig
                val chosenProcessDefinition = modelDataForType.processDefinition
                val subprocessInputs = fetchSubprocessInputs(subprocessVersions, modelDataForType.modelClassLoader.classLoader)
                val subprocessesDetails = subprocessRepository.loadSubprocesses(subprocessVersions)
                val uiProcessDefinition = UIProcessDefinition(chosenProcessDefinition, subprocessInputs)

                val fixedNodesConfig = processConfig.getOrElse[Map[String, SingleNodeConfig]]("nodes", Map.empty)
                val dynamicNodesConfig = uiProcessDefinition.allDefinitions.mapValues(_.nodeConfig)
                val nodesConfig = NodesConfigCombiner.combine(fixedNodesConfig, dynamicNodesConfig)

                val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map { case (k, v) => (k, v.defaultValues.getOrElse(Map.empty)) })
                val defaultParametersFactory = DefaultValueExtractorChain(defaultParametersValues, modelDataForType.modelClassLoader)

                val nodeCategoryMapping = processConfig.getOrElse[Map[String, String]]("nodeCategoryMapping", Map.empty)
                val additionalPropertiesConfig = processConfig.getOrElse[Map[String, AdditionalProcessProperty]]("additionalFieldsConfig", Map.empty)

                val result = ProcessObjects(
                  nodesToAdd = DefinitionPreparer.prepareNodesToAdd(
                    user = user,
                    processDefinition = chosenProcessDefinition,
                    isSubprocess = isSubprocess,
                    subprocessInputs = subprocessInputs,
                    extractorFactory = defaultParametersFactory,
                    nodesConfig = nodesConfig,
                    nodeCategoryMapping = nodeCategoryMapping
                  ),
                  processDefinition = uiProcessDefinition,
                  nodesConfig = nodesConfig,
                  additionalPropertiesConfig = additionalPropertiesConfig,
                  edgesForNodes = DefinitionPreparer.prepareEdgeTypes(
                    user = user,
                    processDefinition = chosenProcessDefinition,
                    isSubprocess = isSubprocess,
                    subprocessesDetails = subprocessesDetails))
                HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.asJson.toString()))
              }.getOrElse {
                HttpResponse(status = StatusCodes.NotFound, entity = s"Processing type: $processingType not found")
              }
              response
            }
          }
        }
      }
    } ~ path("processDefinitionData" / "componentIds") {
      get {
        complete {
          val subprocessIds = subprocessRepository.loadSubprocesses().map(_.canonical.metaData.id).toList
          ProcessObjectsFinder.componentIds(modelData.values.map(_.processDefinition).toList, subprocessIds)
        }
      }
    } ~ path("processDefinitionData" / "services") {
      get {
        complete {
          val result = modelData.mapValues(_.processDefinition.services)
          HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.asJson.toString()))
        }
      }
    }
  }

  private def fetchSubprocessInputs(subprocessVersions: Map[String, Long], classLoader: ClassLoader): Map[String, ObjectDefinition] = {
    val subprocessInputs = subprocessRepository.loadSubprocesses(subprocessVersions).collect {
      case SubprocessDetails(CanonicalProcess(MetaData(id, _, _, _, _), _, FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _), category) =>
        val clazzRefParams = parameters.map { p =>
          //TODO: currently if we cannot parse parameter class we assume it's unknown
          val classRef = p.typ.toClazzRef(classLoader).getOrElse(ClazzRef.unknown)
          definition.Parameter(p.name, classRef, classRef, ParameterTypeMapper.prepareRestrictions(classRef.clazz, None))
        }
        (id, ObjectDefinition(clazzRefParams, ClazzRef[java.util.Map[String, Any]], List(category)))
    }.toMap
    subprocessInputs
  }

}

case class ProcessObjects(nodesToAdd: List[NodeGroup],
                          processDefinition: UIProcessDefinition,
                          nodesConfig: Map[String, SingleNodeConfig],
                          additionalPropertiesConfig: Map[String, AdditionalProcessProperty],
                          edgesForNodes: List[NodeEdges])

case class UIProcessDefinition(services: Map[String, ObjectDefinition],
                               sourceFactories: Map[String, ObjectDefinition],
                               sinkFactories: Map[String, ObjectDefinition],
                               customStreamTransformers: Map[String, ObjectDefinition],
                               signalsWithTransformers: Map[String, ObjectDefinition],
                               exceptionHandlerFactory: ObjectDefinition,
                               globalVariables: Map[String, ObjectDefinition],
                               typesInformation: List[ClazzDefinition],
                               subprocessInputs: Map[String, ObjectDefinition]) {
  // skipping exceptionHandlerFactory
  val allDefinitions: Map[String, ObjectDefinition] = services ++ sourceFactories ++ sinkFactories ++
    customStreamTransformers ++ signalsWithTransformers ++ globalVariables ++ subprocessInputs
}

case class AdditionalProcessProperty(label: String, `type`: PropertyType.Value, default: Option[String], isRequired: Boolean, values: Option[List[String]])

object AdditionalProcessProperty {
  import argonaut.Argonaut._
  implicit val propertyTypeCodec: CodecJson[PropertyType.Value] =
    CodecJson[PropertyType.Value](v => jString(v.toString), h => h.as[String].map(PropertyType.withName))
  implicit val jsonCodec: CodecJson[AdditionalProcessProperty] = CodecJson.derive[AdditionalProcessProperty]
}

object PropertyType extends Enumeration {
  type PropertyType = Value
  val select, text, string, integer = Value
}

object UIProcessDefinition {
  def apply(processDefinition: ProcessDefinition[ObjectDefinition], subprocessInputs: Map[String, ObjectDefinition]): UIProcessDefinition = {
    val uiProcessDefinition = UIProcessDefinition(
      services = processDefinition.services,
      sourceFactories = processDefinition.sourceFactories,
      sinkFactories = processDefinition.sinkFactories,
      subprocessInputs = subprocessInputs,
      customStreamTransformers = processDefinition.customStreamTransformers.mapValues(_._1),
      signalsWithTransformers = processDefinition.signalsWithTransformers.mapValues(_._1),
      exceptionHandlerFactory = processDefinition.exceptionHandlerFactory,
      globalVariables = processDefinition.expressionConfig.globalVariables,
      typesInformation = processDefinition.typesInformation
    )
    uiProcessDefinition
  }
}

case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String])

object SortedNodeGroup {
  def apply(name: String, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label.toLowerCase))
}

case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])

//TODO: some refactoring?
import PermissionSyntax._, pl.touk.nussknacker.ui.security.api.Permission._

object DefinitionPreparer {

  case class NodeTypeId(`type`: String, id: Option[String] = None)

  case class NodeEdges(nodeId: NodeTypeId, edges: List[EdgeType], canChooseNodes: Boolean)

  def prepareNodesToAdd(user: LoggedUser,
                        processDefinition: ProcessDefinition[ObjectDefinition],
                        isSubprocess: Boolean,
                        subprocessInputs: Map[String, ObjectDefinition],
                        extractorFactory: ParameterDefaultValueExtractorStrategy,
                        nodesConfig: Map[String, SingleNodeConfig],
                        nodeCategoryMapping: Map[String, String]
                       ): List[NodeGroup] = {
    val evaluator = new ParameterEvaluatorExtractor(extractorFactory)
    val readCategories = user.can(Read).toList
    def filterCategories(objectDefinition: ObjectDefinition) = readCategories.intersect(objectDefinition.categories)

    def objDefParams(id: String, objDefinition: ObjectDefinition): List[Parameter] = evaluator.evaluateParameters(NodeDefinition(id, objDefinition.parameters))

    def serviceRef(id: String, objDefinition: ObjectDefinition) = ServiceRef(id, objDefParams(id, objDefinition))

    val returnsUnit = ((id: String, objectDefinition: ObjectDefinition)
    => objectDefinition.returnType == Typed[BoxedUnit]).tupled

    val base = NodeGroup("base", List(
      NodeToAdd("filter", "filter", Filter("", Expression("spel", "true")), readCategories),
      NodeToAdd("split", "split", Split(""), readCategories),
      NodeToAdd("switch", "switch", Switch("", Expression("spel", "true"), "output"), readCategories),
      NodeToAdd("variable", "variable", Variable("", "varName", Expression("spel", "'value'")), readCategories),
      NodeToAdd("sqlVariable", "sqlVariable", Variable("", "varName", Expression("sql", "SELECT * FROM input")), readCategories)
    ))
    val services = NodeGroup("services",
      processDefinition.services.filter(returnsUnit).map {
        case (id, objDefinition) => NodeToAdd("processor", id,
          Processor("", serviceRef(id, objDefinition)), filterCategories(objDefinition))
      }.toList
    )

    val enrichers = NodeGroup("enrichers",
      processDefinition.services.filterNot(returnsUnit).map {
        case (id, objDefinition) => NodeToAdd("enricher", id,
          Enricher("", serviceRef(id, objDefinition), "output"), filterCategories(objDefinition))
      }.toList
    )

    val customTransformers = NodeGroup("custom",
      processDefinition.customStreamTransformers.map {
        case (id, (objDefinition, _)) => NodeToAdd("customNode", id,
          CustomNode("", if (objDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, objDefinition)), filterCategories(objDefinition))
      }.toList
    )

    val subprocessDependent = if (!isSubprocess) {
      List(
      NodeGroup("sinks",
        processDefinition.sinkFactories.map {
          case (id, objDefinition) => NodeToAdd("sink", id,
            Sink("", SinkRef(id, objDefParams(id, objDefinition)),
              Some(Expression("spel", "#input"))), filterCategories(objDefinition)
          )
        }.toList),
      NodeGroup("sources",
        processDefinition.sourceFactories.map {
          case (id, objDefinition) => NodeToAdd("source", id,
            Source("", SourceRef(id, objDefParams(id, objDefinition))),
            filterCategories(objDefinition)
          )
        }.toList),
      //so far we don't allow nested subprocesses...
      NodeGroup("subprocesses",
        subprocessInputs.map {
          case (id, definition) =>
            val nodes = evaluator.evaluateParameters(NodeDefinition(id, definition.parameters))
            NodeToAdd("subprocess", id, SubprocessInput("", SubprocessRef(id, nodes)), readCategories.intersect(definition.categories))
        }.toList))
    } else {
      List(
      NodeGroup("subprocessDefinition", List(
        NodeToAdd("input", "input", SubprocessInputDefinition("", List()), readCategories),
        NodeToAdd("output", "output", SubprocessOutputDefinition("", "output"), readCategories)
      )))
    }

    def getNodeCategory(nodeName: String, category: String): String ={
      nodesConfig.get(nodeName).flatMap(_.category).orElse(nodeCategoryMapping.get(category)).getOrElse(category)
    }

    (List(base, services, enrichers, customTransformers) ++ subprocessDependent)
      .flatMap(e => e.possibleNodes.map(n => (e.name, n)))
      .groupBy(e => getNodeCategory(e._2.label, e._1))
      .mapValues(v => v.map(e => e._2))
      .map { case (name: String, elements: List[NodeToAdd]) => SortedNodeGroup(name, elements) }
      .toList
      .sortBy(_.name.toLowerCase)

  }

  def prepareEdgeTypes(user: LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition],
                       isSubprocess: Boolean, subprocessesDetails: Set[SubprocessDetails]): List[NodeEdges] = {

    val subprocessOutputs = if (isSubprocess) List() else subprocessesDetails.map(_.canonical).map { process =>
      val outputs = ProcessConverter.findNodes(process).collect {
        case SubprocessOutputDefinition(_, name, _) => name
      }
      //TODO: enable choice of output type
      NodeEdges(NodeTypeId("SubprocessInput", Some(process.metaData.id)), outputs.map(EdgeType.SubprocessOutput), canChooseNodes = false)
    }

    List(
      NodeEdges(NodeTypeId("Split"), List(), canChooseNodes = true),
      NodeEdges(NodeTypeId("Switch"), List(
        EdgeType.NextSwitch(Expression("spel", "true")), EdgeType.SwitchDefault), canChooseNodes = true),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), canChooseNodes = false)
    ) ++ subprocessOutputs
  }
}
