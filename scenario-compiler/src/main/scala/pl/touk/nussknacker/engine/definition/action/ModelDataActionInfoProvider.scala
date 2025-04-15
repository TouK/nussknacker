package pl.touk.nussknacker.engine.definition.action

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, StaticParameterConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class ModelDataActionInfoProvider(modelData: ModelData) extends ActionInfoProvider with LazyLogging {
  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  override def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): ValidatedNel[
    ProcessCompilationError,
    Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, StaticParameterConfig]]]
  ] = {
    val jobData = JobData(scenario.metaData, processVersion)
    // FIXME abr
    val scenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)
    commonModelDataInfoProvider
      .compileAllCustomNodes(scenario)(scenarioCompilationDependencies)
      .map(nodes => extractParametersFromCustomNodes(nodes))

  }

  private def extractParametersFromCustomNodes(
      compiledCustomNodes: Map[NodeComponentInfo, Any]
  ): Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, StaticParameterConfig]]] = {
    val nodeToActionToParameters = compiledCustomNodes
      .mapValuesNow {
        case s: WithActionParametersSupport => Some(s.actionParametersDefinition)
        case _                              => None
      }
      .collect { case (componentInfo, Some(value)) =>
        componentInfo -> value
      }
    groupByAction(nodeToActionToParameters)
  }

  private def groupByAction(
      nodeToActionToParams: Map[NodeComponentInfo, Map[ScenarioActionName, Map[ParameterName, StaticParameterConfig]]]
  ): Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, StaticParameterConfig]]] = {
    val actionToNodeToParameters = for {
      (node, actionToParams) <- nodeToActionToParams.toList
      (actionName, params)   <- actionToParams.toList
    } yield (actionName, node -> params)
    actionToNodeToParameters
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toMap)
  }

}
