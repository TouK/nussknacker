package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.standalone.api.StandaloneCustomTransformer
import pl.touk.nussknacker.engine.standalone.api.types.InterpreterType

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object ProcessSplitter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("parts") parts: LazyParameter[java.util.Collection[Any]]): StandaloneCustomTransformer = {
    new ProcessSplitter(parts)
  }

}

class ProcessSplitter(parts: LazyParameter[java.util.Collection[Any]])
  extends StandaloneCustomTransformer with ReturningType {

  override def createTransformation(outputVariable: Option[String]): StandaloneCustomTransformation =
    (continuation: InterpreterType, lpi: LazyParameterInterpreter) => (ctx, ec) => {
      implicit val ecc: ExecutionContext = ec
      lpi.createInterpreter(parts)(ec, ctx).flatMap { partsToInterpret =>
        Future.sequence(partsToInterpret.asScala.toList.map { partToInterpret =>
          val newCtx = ctx.withVariable(outputVariable.get, partToInterpret)
          continuation(newCtx, ec)
        })
      }.map { results: List[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], List[InterpretationResult]]] =>
        results.sequence.right.map(_.flatten)
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
