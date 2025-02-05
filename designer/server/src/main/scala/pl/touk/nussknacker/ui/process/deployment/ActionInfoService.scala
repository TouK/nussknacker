package pl.touk.nussknacker.ui.process.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.RawParameterEditor
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.action.ActionInfoProvider
import pl.touk.nussknacker.restmodel.definition.UiActionParameterConfig
import pl.touk.nussknacker.ui.process.deployment.ActionInfoService.{UiActionNodeParameters, UiActionParameters}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

class ActionInfoService(actionInfoProvider: ActionInfoProvider, processResolver: UIProcessResolver) {

  def getActionParameters(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(
      implicit user: LoggedUser
  ): UiActionParameters = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    actionInfoProvider
      .getActionParameters(processVersion, canonical)
      .map { case (scenarioActionName, nodeParamsMap) =>
        scenarioActionName -> nodeParamsMap.map { case (nodeId, params) =>
          UiActionNodeParameters(
            nodeId,
            params.map { case (name, value) =>
              name.value -> UiActionParameterConfig(
                value.defaultValue,
                value.editor.getOrElse(RawParameterEditor),
                value.label,
                value.hintText
              )
            }
          )
        }.toList
      }
  }

  // copied from ScenarioTestService
  private def toCanonicalProcess(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit user: LoggedUser): CanonicalProcess = {
    processResolver.validateAndResolve(scenarioGraph, processVersion, isFragment)
  }

}

object ActionInfoService {
  @JsonCodec case class UiActionNodeParameters(nodeId: NodeId, parameters: Map[String, UiActionParameterConfig])
  type UiActionParameters = Map[ScenarioActionName, List[UiActionNodeParameters]]
}
