package pl.touk.nussknacker.engine.definition.action

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class ModelDataActionInfoProvider(modelData: ModelData)
    extends CommonModelDataInfoProvider(modelData)
    with ActionInfoProvider {

  override def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Map[ScenarioActionName, Map[NodeId, Map[ParameterName, ParameterConfig]]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    modelData.withThisAsContextClassLoader {
      val nodeToActionToParameters = collectAllSources(scenario)
        .map(source => NodeId(source.id) -> getActionParameters(source, jobData))
        .toMap
      groupByAction(nodeToActionToParameters)
    }
  }

  private def groupByAction(
      nodeToActionToParameters: Map[NodeId, Map[ScenarioActionName, Map[ParameterName, ParameterConfig]]]
  ): Map[ScenarioActionName, Map[NodeId, Map[ParameterName, ParameterConfig]]] = {
    val actionToNodeToParameters = for {
      (node, actionToParams) <- nodeToActionToParameters.toList
      (actionName, params)   <- actionToParams.toList
    } yield (actionName, node -> params)
    actionToNodeToParameters
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toMap)
  }

  private def getActionParameters(
      source: SourceNodeData,
      jobData: JobData
  ): Map[ScenarioActionName, Map[ParameterName, ParameterConfig]] = {
    modelData.withThisAsContextClassLoader {
      val compiledSource = compileSourceNode(source)(jobData, NodeId(source.id))
      compiledSource match {
        case Valid(s: WithActionParametersSupport) => s.actionParametersDefinition
        case _                                     => Map.empty
      }
    }
  }

}
