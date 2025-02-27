package pl.touk.nussknacker.engine.definition.action

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, ParameterConfig}
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class ModelDataActionInfoProvider(modelData: ModelData) extends ActionInfoProvider {
  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  override def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, ParameterConfig]]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    modelData.withThisAsContextClassLoader {
      val abc = commonModelDataInfoProvider
        .collectAndCompileAllSourcesAndServices(scenario)(jobData)
      val nodeToActionToParameters = abc
        .mapValuesNow {
          case s: WithActionParametersSupport => s.actionParametersDefinition
          case _                              => Map.empty[ScenarioActionName, Map[ParameterName, ParameterConfig]]
        }
      groupByAction(nodeToActionToParameters)
    }
  }

  private def groupByAction(
      nodeToActionToParameters: Map[NodeComponentInfo, Map[ScenarioActionName, Map[ParameterName, ParameterConfig]]]
  ): Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, ParameterConfig]]] = {
    val actionToNodeToParameters = for {
      (node, actionToParams) <- nodeToActionToParameters.toList
      (actionName, params)   <- actionToParams.toList
    } yield (actionName, node -> params)
    actionToNodeToParameters
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toMap)
  }

}
