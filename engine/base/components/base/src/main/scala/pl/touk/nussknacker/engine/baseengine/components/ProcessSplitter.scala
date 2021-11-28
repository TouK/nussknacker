package pl.touk.nussknacker.engine.baseengine.components

import cats.Monad
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.CustomComponentContext
import pl.touk.nussknacker.engine.baseengine.api.utils.transformers.SingleElementComponent

import scala.collection.JavaConverters._
import scala.language.higherKinds

object ProcessSplitter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("parts") parts: LazyParameter[java.util.Collection[Any]],
             @OutputVariableName outputVariable: String): SingleElementComponent = {
    new ProcessSplitterComponent(parts, outputVariable)
  }

}

class ProcessSplitterComponent(parts: LazyParameter[java.util.Collection[Any]], outputVariable: String)
  extends SingleElementComponent with ReturningType {


  override def createSingleTransformation[F[_]:Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): Context => F[ResultType[Result]] = {
    val interpreter = context.interpreter.syncInterpretationFunction(parts)
    (ctx: Context) => {
      val partsToRun = interpreter(ctx)
      val partsToInterpret = partsToRun.asScala.toList.map { partToRun =>
        ctx.withVariable(outputVariable, partToRun)
      }
      continuation(DataBatch(partsToInterpret))
    }

  }

  override def returnType: typing.TypingResult = {
    parts.returnType match {
      case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Collection[_]]) && tc.objType.params.nonEmpty =>
        tc.objType.params.head
      case _ => Unknown
    }
  }
}