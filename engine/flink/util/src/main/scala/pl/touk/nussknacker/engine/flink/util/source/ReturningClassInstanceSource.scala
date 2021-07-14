package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory

class ReturningClassInstanceSource extends FlinkSourceFactory[Any]  {

  @MethodToInvoke
  def source(@ParamName("Allowed class")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )  allowedClass: String) =
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, List.empty, None, Typed.typedClass(Class.forName(allowedClass)))

}
case class ReturningTestCaseClass(someMethod: String)