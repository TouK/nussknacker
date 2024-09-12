package pl.touk.nussknacker.ui.process.newactivity

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.RawParameterEditor
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.activity.ActivityInfoProvider
import pl.touk.nussknacker.restmodel.definition.UiActivityParameterConfig
import pl.touk.nussknacker.ui.process.newactivity.ActivityInfoService.{ActivityName, UiActivityNodeParameters}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

// TODO: move to ActivityService? execute node compilation only once with ScenarioTestService?
class ActivityInfoService(activityInfoProvider: ActivityInfoProvider, processResolver: UIProcessResolver) {

  def getActivityParameters(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(
      implicit user: LoggedUser
  ): Map[ActivityName, List[UiActivityNodeParameters]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    activityInfoProvider
      .getActivityParameters(processVersion, canonical)
      .map { case (activityName, nodeParamsMap) =>
        activityName -> nodeParamsMap.map { case (nodeId, params) =>
          UiActivityNodeParameters(
            NodeId(nodeId),
            params.map { case (name, value) =>
              name -> UiActivityParameterConfig(
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

object ActivityInfoService {
  type ActivityName = String
  @JsonCodec case class UiActivityNodeParameters(nodeId: NodeId, parameters: Map[String, UiActivityParameterConfig])
}
