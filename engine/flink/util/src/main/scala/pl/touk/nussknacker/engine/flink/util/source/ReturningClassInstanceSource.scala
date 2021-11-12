package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}

class ReturningClassInstanceSource extends SourceFactory[Any]  {

  @MethodToInvoke
  def source(@ParamName("Additional class")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )  additionalClass: String) =
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, List.empty, None, Typed.typedClass(Class.forName(additionalClass)))

}
case class ReturningTestCaseClass(someMethod: String)