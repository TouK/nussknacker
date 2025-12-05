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
            description =
              "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat"
          ),
          new MultiSelectLabeledValue(
            value = "option-with-description-2",
            label = "Option with description 2",
            description =
              "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, " +
                "eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo."
          ),
        )
      )
      @Editor(`type` = EditorType.JSON_EDITOR)
      // When defining a `MULTI_SELECT_EDITON` parameter, the expected type should be set to `Any`, not `io.circe.Json`,
      // even though the runtime value will be `io.circe.Json`
      multiSelect: Any,
      @ParamName("multiSelectGroupingParam")
      @Editor(
        `type` = EditorType.MULTI_SELECT_EDITOR,
        possibleMultiSelectValues = Array(
          new MultiSelectLabeledValue(
            value = "foo/option",
            label = "Option 1",
            description =
              "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat",
            group = "Group Foo"
          ),
          new MultiSelectLabeledValue(
            value = "foo/option2",
            label = "Option 2",
            description =
              "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, " +
                "eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.",
            group = "Group Foo"
          ),
          new MultiSelectLabeledValue(
            value = "bar/option",
            label = "Option 1",
            description =
              "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat",
            group = "Group Bar"
          ),
          new MultiSelectLabeledValue(
            value = "bar/option2",
            label = "Option 2",
            description =
              "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, " +
                "eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.",
            group = "Group Bar"
          ),
        )
      )
      @Editor(`type` = EditorType.JSON_EDITOR)
      // When defining a `MULTI_SELECT_EDITON` parameter, the expected type should be set to `Any`, not `io.circe.Json`,
      // even though the runtime value will be `io.circe.Json`
      multiSelectWithGrouping: Any,
  ): Future[Any] = {
    Future.successful(multiSelect)
  }

}
