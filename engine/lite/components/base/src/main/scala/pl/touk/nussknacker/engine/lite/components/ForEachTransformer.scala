package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, LiteCustomComponent}
import pl.touk.nussknacker.engine.lite.api.utils.transformers.SingleElementComponent

import scala.jdk.CollectionConverters._
import scala.language.higherKinds

object ForEachTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(
      @ParamName("Elements") elements: LazyParameter[java.util.Collection[Any]],
      @OutputVariableName outputVariable: String
  ): LiteCustomComponent = {
    new ForEachTransformerComponent(elements, outputVariable)
  }

}

class ForEachTransformerComponent(elements: LazyParameter[java.util.Collection[Any]], outputVariable: String)
    extends LiteCustomComponent
    with ReturningType {

  final override def createTransformation[F[_]: Monad, Result](
      continuation: DataBatch => F[ResultType[Result]],
      context: CustomComponentContext[F]
  ): DataBatch => F[ResultType[Result]] = { batch =>
    continuation(DataBatch(batch.value.flatMap { ctx =>
      val partsToRun = elements.evaluate(ctx)
      partsToRun.asScala.toList.zipWithIndex.map { case (partToRun, index) =>
        ctx.withVariable(outputVariable, partToRun).appendIdSuffix(index.toString)
      }
    }))
  }

  override def returnType: typing.TypingResult = {
    elements.returnType match {
      case tc: SingleTypingResult
          if tc.runtimeObjType.canBeImplicitlyConvertedTo(
            Typed[java.util.Collection[_]]
          ) && tc.runtimeObjType.params.nonEmpty =>
        tc.runtimeObjType.params.head
      case _ => Unknown
    }
  }

}
