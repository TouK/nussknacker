package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CustomComponentContext
import pl.touk.nussknacker.engine.lite.api.utils.transformers.SingleElementComponent

import scala.collection.JavaConverters._
import scala.language.higherKinds

object ForEachTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("Elements") elements: LazyParameter[java.util.Collection[Any]],
             @OutputVariableName outputVariable: String): SingleElementComponent = {
    new ForEachTransformerComponent(elements, outputVariable)
  }

}

class ForEachTransformerComponent(elements: LazyParameter[java.util.Collection[Any]], outputVariable: String)
  extends SingleElementComponent with ReturningType {


  override def createSingleTransformation[F[_]:Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): Context => F[ResultType[Result]] = {
    val interpreter = context.interpreter.syncInterpretationFunction(elements)
    (ctx: Context) => {
      val partsToRun = interpreter(ctx)
      val partsToInterpret = partsToRun.asScala.toList.zipWithIndex.map { case (partToRun, index) =>
        ctx.withVariable(outputVariable, partToRun).copy(id=s"${ctx.id}-$index")
      }
      continuation(DataBatch(partsToInterpret))
    }

  }

  override def returnType: typing.TypingResult = {
    elements.returnType match {
      case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Collection[_]]) && tc.objType.params.nonEmpty =>
        tc.objType.params.head
      case _ => Unknown
    }
  }
}
