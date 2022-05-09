package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, JoinContextTransformationDef, OutputVar}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, LiteCustomComponent}

import scala.collection.JavaConverters._
import scala.language.higherKinds

object CollectorTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("maxCount") maxCount: Option[Integer],
             @ParamName("toCollect") toCollect: LazyParameter[AnyRef],
             @OutputVariableName outputVariable: String)
            (implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy { context =>
        val outputType = Typed.typedClass(classOf[java.util.List[_]], toCollect.returnType :: Nil)
        context.withVariable(OutputVar.variable(outputVariable), outputType)
      }.implementedBy(
      new CollectorTransformer(outputVariable, maxCount, toCollect)
    )
  }
}

class CollectorTransformer(outputVariable: String, maxCount: Option[Integer], toCollect: LazyParameter[AnyRef])(implicit nodeId: NodeId) extends LiteCustomComponent with Lifecycle with LazyLogging {

  private var runtimeContext: EngineRuntimeContext = _

  override def open(context: EngineRuntimeContext): Unit = runtimeContext = context

  override def createTransformation[F[_]:Monad, Result](continuation: DataBatch => F[ResultType[Result]],
                                                        context: CustomComponentContext[F]): DataBatch => F[ResultType[Result]] = {

    val outputInterpreter = context.interpreter.syncInterpretationFunction(toCollect)

    // TODO: this lazy val is tricky - we should instead assign ContextIdGenerator in open, but we don't have nodeId in open
    lazy val contextIdGenerator = runtimeContext.contextIdGenerator(context.nodeId)
    (inputCtx: DataBatch) =>
      val rawValues = inputCtx.map(outputInterpreter(_))
      val outputs = maxCount match {
        case Some(limit) => rawValues.take(limit).asJava
        case None => rawValues.asJava
      }
      continuation(DataBatch(Context(contextIdGenerator.nextContextId()).withVariable(outputVariable, outputs) :: Nil))
  }

}