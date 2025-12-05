package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType, MultiSelectLabeledValue}

import scala.concurrent.Future

object MultiSelectEditorService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("multiSelectParam")
      @Editor(
        `type` = EditorType.MULTI_SELECT_EDITOR,
        possibleMultiSelectValues = Array(
          new MultiSelectLabeledValue(value = "option1", label = "option1"),
          new MultiSelectLabeledValue(value = "option2", label = "option2"),
          new MultiSelectLabeledValue(
            value = "option-with-description",
            label = "Option with description",
            description = "Some description"
          ),
          new MultiSelectLabeledValue(
            value = "option-with-icon",
            label = "Option with icon",
            icon = "/assets/components/Filter.svg"
          ),
          new MultiSelectLabeledValue(
            value = "option-with-both-description-and-icon",
            label = "Option with both description and icon",
            description = "Some description",
            icon = "/assets/components/CustomNode.svg"
          ),
        )
      )
      @Editor(`type` = EditorType.JSON_EDITOR)
      // When defining a `MULTI_SELECT_EDITON` parameter, the expected type should be set to `Any`, not `io.circe.Json`,
      // even though the runtime value will be `io.circe.Json`
      multiSelect: Any,
  ): Future[Any] = {
    Future.successful(multiSelect)
  }

}
