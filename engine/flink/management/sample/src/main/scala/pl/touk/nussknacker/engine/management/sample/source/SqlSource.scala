package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

//It's only for test FE sql editor
object SqlSource extends SourceFactory {

  @MethodToInvoke
  def source(@ParamName("sql")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.SQL_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )  sql: String) =
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, List.empty, None, Unknown)
}
