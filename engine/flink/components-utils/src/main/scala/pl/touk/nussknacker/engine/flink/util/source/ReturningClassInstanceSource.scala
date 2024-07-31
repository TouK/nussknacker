package pl.touk.nussknacker.engine.flink.util.source

import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}

class ReturningClassInstanceSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(
      @ParamName("Additional class")
      @DualEditor(
        simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
        defaultMode = DualEditorMode.SIMPLE
      ) additionalClass: String
  ) = {
    val resultClass = Class.forName(additionalClass)
    CollectionSource[Any](List.empty, None, Typed.typedClass(resultClass))
  }

}

case class ReturningTestCaseClass(someMethod: String)
