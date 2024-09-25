package pl.touk.nussknacker.engine.definition.activity

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.process.WithActivityParameters
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class ModelDataActivityInfoProvider(modelData: ModelData)
    extends CommonModelDataInfoProvider(modelData)
    with ActivityInfoProvider {

  override def getActivityParameters(
      scenario: CanonicalProcess
  ): Map[String, Map[String, Map[String, ParameterConfig]]] = {
    modelData.withThisAsContextClassLoader {
      val nodeToActivityToParameters = collectAllSources(scenario)
        .map(source => source.id -> getActivityParameters(source, scenario.metaData))
        .toMap
      groupByActivity(nodeToActivityToParameters)
    }
  }

  private def groupByActivity(
      nodeToActivityToParameters: Map[String, Map[String, Map[String, ParameterConfig]]]
  ): Map[String, Map[String, Map[String, ParameterConfig]]] = {
    val activityToNodeToParameters = for {
      (node, activityToParams) <- nodeToActivityToParameters.toList
      (activity, params)       <- activityToParams.toList
    } yield (activity, node -> params)
    activityToNodeToParameters
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toMap)
  }

  private def getActivityParameters(
      source: SourceNodeData,
      metaData: MetaData
  ): Map[String, Map[String, ParameterConfig]] = {
    modelData.withThisAsContextClassLoader {
      val compiledSource = prepareSourceObj(source)(metaData, NodeId(source.id))
      compiledSource match {
        case Some(s: WithActivityParameters) => s.activityParametersDefinition
        case _                               => Map.empty
      }
    }
  }

}
