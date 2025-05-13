package pl.touk.nussknacker.engine.lite.api.utils

import cats.{Monad, Monoid}
import cats.data.Writer
import cats.implicits._
import pl.touk.nussknacker.engine.api.{Context, LazyParameter}
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.lite.api.commonTypes.{monoid, DataBatch, ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, LiteSink}
import pl.touk.nussknacker.engine.lite.api.utils.errors.withErrors

import scala.language.higherKinds

object sinks {

  trait SingleContextSink[Res] extends LiteSink[Res] {

    def createSingleTransformation[F[_]: Monad](
        context: CustomComponentContext[F]
    ): (TypingResult, Context => F[Either[ErrorType, Res]])

    override def createTransformation[F[_]: Monad](
        context: CustomComponentContext[F]
    ): (TypingResult, DataBatch => F[ResultType[(Context, Res)]]) = {
      val (typeResult, invocation) = createSingleTransformation[F](context)
      (
        typeResult,
        ctxs => {
          Monoid.combineAll(ctxs.map { ctx =>
            invocation(ctx).map[ResultType[(Context, Res)]] {
              case Left(error)   => Writer(List(error), Nil)
              case Right(output) => Writer.value((ctx, output) :: Nil)
            }
          })
        }
      )
    }

  }

  trait LazyParamSink[Res <: AnyRef] extends SingleContextSink[Res] {

    def prepareResponse: LazyParameter[Res]

    override def createSingleTransformation[F[_]: Monad](
        context: CustomComponentContext[F]
    ): (TypingResult, Context => F[Either[ErrorType, Res]]) = {
      val response = prepareResponse
      (
        response.returnType,
        ctx =>
          implicitly[Monad[F]].pure(
            // FIXME: figure out how to pass componentName here
            withErrors(context, Some(ComponentId(ComponentType.Sink, "unknown")), ctx) {
              response.evaluate(ctx)
            }
          )
      )
    }

  }

}
