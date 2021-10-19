package pl.touk.nussknacker.engine.baseengine.api.utils

import cats.{Monad, Monoid}
import cats.data.Writer
import cats.implicits._
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter}
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.{DataBatch, ErrorType, ResultType, monoid}
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.{BaseEngineSink, CustomComponentContext}
import pl.touk.nussknacker.engine.baseengine.api.utils.errors.withErrors

import scala.language.higherKinds

object sinks {

  trait SingleContextSink[Res <: AnyRef] extends BaseEngineSink[Res] {

    def createSingleTransformation[F[_]: Monad](context: CustomComponentContext[F]): (TypingResult, Context => F[Either[ErrorType, Res]])

    override def createTransformation[F[_]: Monad](context: CustomComponentContext[F]): (TypingResult, DataBatch => F[ResultType[(Context, Res)]]) = {
      val (typeResult, invocation) = createSingleTransformation[F](context)
      (typeResult, ctxs => {
        Monoid.combineAll(ctxs.map { ctx =>
          invocation(ctx).map[ResultType[(Context, Res)]] {
            case Left(error) => Writer(List(error), Nil)
            case Right(output) => Writer.value((ctx, output) :: Nil)
          }
        })
      })
    }
  }

  trait LazyParamSink[Res <: AnyRef] extends SingleContextSink[Res] {

    def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[Res]

    override def createSingleTransformation[F[_]: Monad](context: CustomComponentContext[F]): (TypingResult, Context => F[Either[ErrorType, Res]]) = {
      val response = prepareResponse(context.interpreter)
      val interpreter = context.interpreter.syncInterpretationFunction(response)
      (response.returnType, ctx => implicitly[Monad[F]].pure(
        withErrors(context, ctx) {
          interpreter(ctx)
        }
      ))
    }
  }

}
