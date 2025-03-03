package pl.touk.nussknacker.engine.definition.action

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, ParameterConfig}
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class ModelDataActionInfoProvider(modelData: ModelData) extends ActionInfoProvider with LazyLogging {
  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  override def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[ActionInfoError, Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, ParameterConfig]]]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    modelData.withThisAsContextClassLoader {
      commonModelDataInfoProvider.compileAllCustomNodes(scenario)(jobData) match {
        case Validated.Valid(compiledCustomNodes) => Right(extractParametersFromCustomNodes(compiledCustomNodes))
        case Validated.Invalid(e) =>
          logger.warn(s"Scenario compilation failed with error: $e while getting action parameters")
          Left(CannotCompileScenario)
      }
    }
  }

  private def extractParametersFromCustomNodes(
      compiledCustomNodes: Map[NodeComponentInfo, Any]
  ): Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, ParameterConfig]]] = {
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
