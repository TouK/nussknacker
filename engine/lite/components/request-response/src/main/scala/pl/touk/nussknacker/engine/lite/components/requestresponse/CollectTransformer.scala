package pl.touk.nussknacker.engine.lite.components.requestresponse

import cats.Monad
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, LiteCustomComponent}

import scala.jdk.CollectionConverters._
import scala.language.higherKinds

object CollectTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(
      @ParamName("Input expression") inputExpression: LazyParameter[AnyRef],
      @OutputVariableName outputVariable: String
  )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy { context =>
        val outputType = Typed.genericTypeClass(classOf[java.util.List[_]], inputExpression.returnType :: Nil)
        context.clearVariables.withVariable(OutputVar.variable(outputVariable), outputType)
      }
      .implementedBy(
        new CollectTransformer(outputVariable, inputExpression)
      )
  }

}

class CollectTransformer(outputVariable: String, inputExpression: LazyParameter[AnyRef])(implicit nodeId: NodeId)
    extends LiteCustomComponent
    with Lifecycle
    with LazyLogging {

  private var runtimeContext: EngineRuntimeContext = _

  override def open(context: EngineRuntimeContext): Unit = runtimeContext = context

  override def createTransformation[F[_]: Monad, Result](
      continuation: DataBatch => F[ResultType[Result]],
      context: CustomComponentContext[F]
  ): DataBatch => F[ResultType[Result]] = {
    // TODO: this lazy val is tricky - we should instead assign ContextIdGenerator in open, but we don't have nodeId in open
    lazy val contextIdGenerator = runtimeContext.contextIdGenerator(context.nodeId)
    (inputCtx: DataBatch) =>
      val outputList = inputCtx.map(inputExpression.evaluate).asJava
      continuation(
        DataBatch(
          Context(contextIdGenerator.nextContextId()).withVariable(outputVariable, outputList) :: Nil
        )
      )
  }

}
