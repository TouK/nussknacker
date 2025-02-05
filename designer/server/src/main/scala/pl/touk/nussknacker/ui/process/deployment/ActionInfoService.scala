package pl.touk.nussknacker.ui.process.deployment

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
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
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    val parameters = actionInfoProvider
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
    UiActionParameters(parameters)
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

  @JsonCodec final case class UiActionParameterConfig(
      defaultValue: Option[String],
      editor: ParameterEditor,
      label: Option[String],
      hintText: Option[String]
  )

  object UiActionParameterConfig {
    def empty: UiActionParameterConfig = UiActionParameterConfig(None, RawParameterEditor, None, None)
  }

  case class UiActionParameters(nameToParameterses: Map[ScenarioActionName, List[UiActionNodeParameters]])

  implicit val uiActionParametersEncoder: Encoder[UiActionParameters] = Encoder
    .encodeMap[ScenarioActionName, List[UiActionNodeParameters]]
    .contramap(_.nameToParameterses)

  implicit val uiActionParametersDecoder: Decoder[UiActionParameters] =
    Decoder.decodeMap[ScenarioActionName, List[UiActionNodeParameters]].map(UiActionParameters)

}
