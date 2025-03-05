package pl.touk.nussknacker.engine.lite.api.utils

import cats.Monad
import cats.implicits._
import cats.kernel.Monoid
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.lite.api.commonTypes._
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes._

import scala.language.higherKinds

object transformers {

  // This is case where were process events one by one, ignoring batching
  trait SingleElementComponent extends LiteCustomComponent {

    final override def createTransformation[F[_]: Monad, Result](
        continuation: DataBatch => F[ResultType[Result]],
        context: CustomComponentContext[F]
    ): DataBatch => F[ResultType[Result]] = {
      val singleTransformation = createSingleTransformation(continuation, context)
      batch => Monoid.combineAll(batch.map(singleTransformation))
    }

    def createSingleTransformation[F[_]: Monad, Result](
        continuation: DataBatch => F[ResultType[Result]],
        context: CustomComponentContext[F]
    ): Context => F[ResultType[Result]]

  }

  // This is case where we don't want to affect invocation flow, just modify context
  // i.e. it's not flatMap but map (but with possible side effects)
  trait ContextMappingComponent extends SingleElementComponent {

    final override def createSingleTransformation[F[_]: Monad, Result](
        continuation: DataBatch => F[ResultType[Result]],
        context: CustomComponentContext[F]
    ): Context => F[ResultType[Result]] = {
      val transformation = createStateTransformation[F](context)
      ctx => transformation(ctx).flatMap(newCtx => continuation(DataBatch(newCtx :: Nil)))
    }

    def createStateTransformation[F[_]: Monad](
        context: CustomComponentContext[F]
    ): Context => F[Context]

  }

}
