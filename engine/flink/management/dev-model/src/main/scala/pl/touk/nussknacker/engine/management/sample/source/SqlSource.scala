package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

//It's only for test FE sql editor
object SqlSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(@ParamName("sql") @SimpleEditor(`type` = SimpleEditorType.SQL_EDITOR) sql: String) =
    new CollectionSource[Any](List.empty, None, Unknown)(TypeInformation.of(classOf[Any]))

}
