package pl.touk.nussknacker.decisiontable

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.NodeDependency
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import scala.concurrent.Future

object DecisionTable extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("Basic Decision Table")
      @SimpleEditor(`type` = SimpleEditorType.TYPED_TABULAR_DATA_EDITOR) tabularData: TabularTypedData,
      @ParamName("Expression") expression: java.lang.Boolean,
      @OutputVariableName outputVariable: String
  )(implicit nodeId: NodeId): Future[Vector[Vector[Any]]] = Future.successful {
    tabularData.rows
//    ContextTransformation
//      .definedBy(_.withVariable(OutputVar.customNode(outputVariable), Typed[Any]))
//      .implementedBy { () =>
//        println("test")
//      }
  }

}

//class DecisionTable2 extends Service with SingleInputGenericNodeTransformation[ServiceInvoker] {
//
//  override type State = Unit
//
//  override val nodeDependencies: List[NodeDependency] = List.empty
//
//  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
//      implicit nodeId: NodeId
//  ): NodeTransformationDefinition = ???
//
//  override def implementation(
//      params: Map[String, Any],
//      dependencies: List[NodeDependencyValue],
//      finalState: Option[Unit]
//  ): ServiceInvoker = ???
//
//}
