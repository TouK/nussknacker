package pl.touk.nussknacker.engine.flink.util.source

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class ReturningClassInstanceSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(
      @ParamName("Additional class")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      additionalClass: String
  ) = {
    val resultClass = Class.forName(additionalClass)
    CollectionSource[Any](List.empty, None, Typed.typedClass(resultClass))
  }

}

case class ReturningTestCaseClass(someMethod: String)
