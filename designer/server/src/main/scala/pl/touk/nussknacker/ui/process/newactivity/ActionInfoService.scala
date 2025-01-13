package pl.touk.nussknacker.ui.process.newactivity

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.RawParameterEditor
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.activity.ActionInfoProvider
import pl.touk.nussknacker.restmodel.definition.UiActionParameterConfig
import pl.touk.nussknacker.ui.process.newactivity.ActionInfoService.UiActionNodeParameters
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

class ActionInfoService(activityInfoProvider: ActionInfoProvider, processResolver: UIProcessResolver) {

  def getActionParameters(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(
      implicit user: LoggedUser
  ): Map[String, List[UiActionNodeParameters]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    activityInfoProvider
      .getActionParameters(processVersion, canonical)
      .map { case (scenarioActionName, nodeParamsMap) =>
        scenarioActionName.value -> nodeParamsMap.map { case (nodeId, params) =>
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
}
