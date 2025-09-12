package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{ComponentId, NodeComponentInfo, StaticParameterConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.StaticParameterEditor
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.action.ActionInfoProvider
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError.{
  CannotCompileScenario => ApiCannotCompileScenario
}
import pl.touk.nussknacker.ui.process.deployment.ActionInfoService.{
  UiActionNodeParameters,
  UiActionParameterConfig,
  UiActionParameters
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import scala.concurrent.{ExecutionContext, Future}

class ActionInfoService(
    actionInfoProvider: ActionInfoProvider,
    processResolver: UIProcessResolver,
    scenarioResolver: ScenarioResolver
) extends LazyLogging {

  def getActionParameters(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(
      implicit user: LoggedUser,
      executionContext: ExecutionContext
  ): Future[Either[ActionInfoError, UiActionParameters]] = {
    val canonical = processResolver.validateAndResolve(scenarioGraph, processVersion, isFragment)
    scenarioResolver
      .resolveScenario(canonical)
      .map { canonicalWithResolvedFragments =>
        canonicalWithResolvedFragments.toEither
          .flatMap(getResolvedCanonicalScenarioActionParameters(_, processVersion).toEither) match {
          case Right(parameters) =>
            Right(toUIActionParameters(parameters))
          case Left(e) =>
            logger.warn(s"Scenario compilation failed with error: $e while getting action parameters")
            Left(ApiCannotCompileScenario)
        }
      }
  }

  def getResolvedCanonicalScenarioActionParameters(
      resolvedCanonicalScenario: CanonicalProcess,
      processVersion: ProcessVersion
  ): ValidatedNel[
    ProcessCompilationError,
    Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, StaticParameterConfig]]]
  ] = {
    actionInfoProvider.getActionParameters(processVersion, resolvedCanonicalScenario)
  }

  private def toUIActionParameters(
      parameters: Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, StaticParameterConfig]]]
  ): UiActionParameters = {
    val mappedParams = parameters
      .map { case (scenarioActionName, nodeParamsMap) =>
        scenarioActionName -> nodeParamsMap.map { case (nodeComponentInfo, params) =>
          UiActionNodeParameters(
            nodeComponentInfo.nodeId,
            nodeComponentInfo.componentId.getOrElse(
              throw new IllegalStateException("ComponentId is not present")
            ),
            params.map { case (name, value) =>
              name.value -> UiActionParameterConfig(
                value.defaultValue,
                value.editor,
                value.label,
                value.hintText
              )
            }
          )
        }.toList
      }
    UiActionParameters(mappedParams)
  }

}

object ActionInfoService {

  case class UiActionParameters(actionNameToParameters: Map[ScenarioActionName, List[UiActionNodeParameters]])

  case class UiActionNodeParameters(
      nodeId: NodeId,
      componentId: ComponentId,
      parameters: Map[String, UiActionParameterConfig]
  )

  case class UiActionParameterConfig(
      defaultValue: Option[String],
      editor: StaticParameterEditor,
      label: Option[String],
      hintText: Option[String]
  )

}
