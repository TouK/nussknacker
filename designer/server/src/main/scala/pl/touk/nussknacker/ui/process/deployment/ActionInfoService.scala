package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.definition.action.ActionInfoProvider
import pl.touk.nussknacker.ui.process.deployment.ActionInfoService.{
  UiActionNodeParameters,
  UiActionParameterConfig,
  UiActionParameters
}
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
    val canonical = processResolver.validateAndResolve(scenarioGraph, processVersion, isFragment)
    val parameters = actionInfoProvider
      .getActionParameters(processVersion, canonical)
      .map { case (scenarioActionName, nodeParamsMap) =>
        scenarioActionName -> nodeParamsMap.map { case (nodeComponentInfo, params) =>
          UiActionNodeParameters(
            NodeId(nodeComponentInfo.nodeId),
            nodeComponentInfo.componentId.getOrElse(throw new IllegalStateException("ComponentId is not present")),
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
    UiActionParameters(parameters)
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
      editor: ParameterEditor,
      label: Option[String],
      hintText: Option[String]
  )

}
