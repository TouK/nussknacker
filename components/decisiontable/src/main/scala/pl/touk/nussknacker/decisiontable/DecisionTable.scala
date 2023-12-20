package pl.touk.nussknacker.decisiontable

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.typed.typing.Typed

object DecisionTable extends CustomStreamTransformer {

  @MethodToInvoke
  def invoke(
      @ParamName("Basic Decision Table") @SimpleEditor(`type` =
        SimpleEditorType.TYPED_TABULAR_DATA_EDITOR
      ) table: Table[Any],
      @ParamName("Expression") expression: LazyParameter[java.lang.Boolean],
      @OutputVariableName outputVariable: String
  )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy(_.withVariable(OutputVar.customNode(outputVariable), Typed[Any]))
      .implementedBy { () =>
        println("test")
      }
  }

}

final case class Table[T](rows: T)
