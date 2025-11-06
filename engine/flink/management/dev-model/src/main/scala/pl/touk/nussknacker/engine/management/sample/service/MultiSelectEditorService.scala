package pl.touk.nussknacker.engine.management.sample.service

import io.circe.Json
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.definition.SelectOption
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType, LabeledExpression, MultiSelectValueOption}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService

import scala.concurrent.Future

object MultiSelectEditorService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("multiSelectParam")
      @Editor(
        `type` = EditorType.MULTI_SELECT_EDITOR,
        possibleMultiSelectValues = Array(
          new MultiSelectValueOption(value = "option1", label = "option1"),
          new MultiSelectValueOption(value = "option2", label = "option2")
        )
      )
      @Editor(`type` = EditorType.JSON_EDITOR)
      multiSelect: Any,
  ): Future[Any] = {
    Future.successful(multiSelect)
  }

}
