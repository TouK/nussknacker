package pl.touk.nussknacker.ui.process.newactivity

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.definition.RawParameterEditor
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.activity.ActivityInfoProvider
import pl.touk.nussknacker.restmodel.definition.UiScenarioPropertyConfig
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

// TODO: move to ActivityService? execute node compilation only once with ScenarioTestService?
class ActivityInfoService(activityInfoProvider: ActivityInfoProvider, processResolver: UIProcessResolver) {

  // TODO: use UiActivityParameterConfig instead of UiScenarioPropertyConfig
  def getActivityParameters(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(
      implicit user: LoggedUser
  ): Map[String, Map[NodeId, Map[String, UiScenarioPropertyConfig]]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processName, isFragment, labels)
    activityInfoProvider
      .getActivityParameters(canonical)
      .map { case (activityName, nodeParamsMap) =>
        activityName -> nodeParamsMap
          .map { case (nodeId, params) =>
            NodeId(nodeId) -> params.map { case (name, value) =>
              name -> UiScenarioPropertyConfig(
                value.defaultValue,
                value.editor.getOrElse(RawParameterEditor),
                value.label,
                value.hintText
              )
            }
          }
      }
  }

  // copied from ScenarioTestService
  private def toCanonicalProcess(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(implicit user: LoggedUser): CanonicalProcess = {
    processResolver.validateAndResolve(scenarioGraph, processName, isFragment, labels)
  }

}
