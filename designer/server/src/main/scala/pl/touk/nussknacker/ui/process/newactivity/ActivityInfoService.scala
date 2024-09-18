package pl.touk.nussknacker.ui.process.newactivity

import pl.touk.nussknacker.engine.api.definition.StringParameterEditor
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.CanBeSubclassDeterminer
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.activity.ActivityInfoProvider
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

// TODO: move to ActivityService? execute node compilation only once with ScenarioTestService?
class ActivityInfoService(activityInfoProvider: ActivityInfoProvider, processResolver: UIProcessResolver) {

  def getActivityParameters(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(
      implicit user: LoggedUser
  ): Map[String, List[UISourceParameters]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processName, isFragment, labels)
    activityInfoProvider
      .getActivityParameters(canonical)
      .map { case (activityName, nodeParamsMap) =>
        activityName -> nodeParamsMap
          .map { case (nodeId, params) =>
            UISourceParameters(nodeId, params.map(DefinitionsService.createUIParameter))
          }
          .map(assignUserFriendlyEditor)
          .toList
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

  // copied from ScenarioTestService
  private def assignUserFriendlyEditor(uiSourceParameter: UISourceParameters): UISourceParameters = {
    val adaptedParameters = uiSourceParameter.parameters.map { uiParameter =>
      if (CanBeSubclassDeterminer.canBeSubclassOf(uiParameter.typ, Typed.apply(classOf[String])).isValid) {
        uiParameter.copy(editor = StringParameterEditor)
      } else {
        uiParameter
      }
    }
    uiSourceParameter.copy(parameters = adaptedParameters)
  }

}
