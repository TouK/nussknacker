package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.api.StandaloneCustomTransformer
import pl.touk.nussknacker.engine.standalone.api.types.InterpreterType

import scala.concurrent.{ExecutionContext, Future}

object ProcessSplitter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  //TODO: handling java collections?
  def invoke(@ParamName("parts") parts: LazyInterpreter[TraversableOnce[Any]]): StandaloneCustomTransformer = {
    new ProcessSplitter(parts.createInterpreter)
  }

}

class ProcessSplitter(partsInterpreter: (ExecutionContext, Context) => Future[TraversableOnce[Any]])
  extends StandaloneCustomTransformer {

  override def createTransformation(outputVariable: String): StandaloneCustomTransformation =
    (continuation: InterpreterType) => (ctx, ec) => {
      implicit val ecc = ec
      //TODO: should this null be here?

      partsInterpreter(ec, ctx).flatMap { partsToInterpret =>
        Future.sequence(partsToInterpret.toList.map { partToInterpret =>
          val newCtx = ctx.withVariable(outputVariable, partToInterpret)
          continuation(newCtx, ec)
        })
      }.map { (results: List[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], List[InterpretationResult]]]) =>
        results.sequenceU.right.map(_.flatten)
      }
    }

}
