package pl.touk.nussknacker.engine.flink.util.source

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType, SpelEditor}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class ReturningClassInstanceSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(
      @ParamName("Additional class")
      @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR)
      @SpelEditor
      additionalClass: String
  ) = {
    val resultClass = Class.forName(additionalClass)
    CollectionSource[Any](List.empty, None, Typed.typedClass(resultClass))
  }

}

case class ReturningTestCaseClass(someMethod: String)
