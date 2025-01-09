package pl.touk.nussknacker.engine.definition.activity

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.process.WithActionParameters
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class ModelDataActionInfoProvider(modelData: ModelData)
    extends CommonModelDataInfoProvider(modelData)
    with ActionInfoProvider {

  override def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Map[String, Map[String, Map[String, ParameterConfig]]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    modelData.withThisAsContextClassLoader {
      val nodeToActivityToParameters = collectAllSources(scenario)
        .map(source => source.id -> getActivityParameters(source, jobData))
        .toMap
      groupByAction(nodeToActivityToParameters)
    }
  }

  private def groupByAction(
      nodeToActionToParameters: Map[String, Map[String, Map[String, ParameterConfig]]]
  ): Map[String, Map[String, Map[String, ParameterConfig]]] = {
    val activityToNodeToParameters = for {
      (node, activityToParams) <- nodeToActionToParameters.toList
      (activity, params)       <- activityToParams.toList
    } yield (activity, node -> params)
    activityToNodeToParameters
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toMap)
  }

  private def getActivityParameters(
      source: SourceNodeData,
      jobData: JobData
  ): Map[String, Map[String, ParameterConfig]] = {
    modelData.withThisAsContextClassLoader {
      val compiledSource = prepareSourceObj(source)(jobData, NodeId(source.id))
      compiledSource match {
        case Valid(s: WithActionParameters) => s.actionParametersDefinition
        case _                              => Map.empty
      }
    }
  }

}
