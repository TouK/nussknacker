package pl.touk.nussknacker.ui.process.newactivity

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.RawParameterEditor
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.activity.ActionInfoProvider
import pl.touk.nussknacker.restmodel.definition.UiActionParameterConfig
import pl.touk.nussknacker.ui.process.newactivity.ActionInfoService.{ActivityName, UiActionNodeParameters}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

class ActionInfoService(activityInfoProvider: ActionInfoProvider, processResolver: UIProcessResolver) {

  def getActionParameters(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(
      implicit user: LoggedUser
  ): Map[ActivityName, List[UiActionNodeParameters]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    activityInfoProvider
      .getActionParameters(processVersion, canonical)
      .map { case (activityName, nodeParamsMap) =>
        activityName -> nodeParamsMap.map { case (nodeId, params) =>
          UiActionNodeParameters(
            NodeId(nodeId),
            params.map { case (name, value) =>
              name -> UiActionParameterConfig(
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
  type ActivityName = String
  @JsonCodec case class UiActionNodeParameters(nodeId: NodeId, parameters: Map[String, UiActionParameterConfig])
}
