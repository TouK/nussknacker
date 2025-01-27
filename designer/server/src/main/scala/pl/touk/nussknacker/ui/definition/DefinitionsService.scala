package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.TemplateEvaluationResult
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.DefinitionsService.ComponentUiConfigMode
import pl.touk.nussknacker.ui.definition.scenarioproperty.UiScenarioPropertyEditorDeterminer
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

trait DefinitionsService {

  def prepareUIDefinitions(
      processingType: ProcessingType,
      forFragment: Boolean,
      componentUiConfigMode: ComponentUiConfigMode
  )(
      implicit user: LoggedUser
  ): Future[UIDefinitions]

}

object DefinitionsService {

  def createUIParameter(parameter: Parameter): UIParameter = {
    UIParameter(
      name = parameter.name.value,
      typ = toUIType(parameter.typ),
      editor = parameter.finalEditor,
      defaultValue = parameter.finalDefaultValue,
      additionalVariables = parameter.additionalVariables.mapValuesNow(_.typingResult),
      variablesToHide = parameter.variablesToHide,
      branchParam = parameter.branchParam,
      hintText = parameter.hintText,
      label = parameter.label,
      requiredParam = Some(!parameter.isOptional),
    )
  }

  private def toUIType(typingResult: TypingResult): TypingResult = {
    if (typingResult == Typed[TemplateEvaluationResult]) Typed[String] else typingResult
  }

  def createUIScenarioPropertyConfig(config: ScenarioPropertyConfig): UiScenarioPropertyConfig = {
    val editor = UiScenarioPropertyEditorDeterminer.determine(config)
    UiScenarioPropertyConfig(config.defaultValue, editor, config.label, config.hintText)
  }

  sealed trait ComponentUiConfigMode

  object ComponentUiConfigMode {
    case object EnrichedWithUiConfig extends ComponentUiConfigMode
    case object BasicConfig          extends ComponentUiConfigMode
  }

}
