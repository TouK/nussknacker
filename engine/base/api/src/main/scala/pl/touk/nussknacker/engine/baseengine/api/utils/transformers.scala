package pl.touk.nussknacker.engine.baseengine.api.utils

import cats.Monad
import cats.implicits._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes._

import scala.language.higherKinds

object transformers {

  //This is case where were process events one by one, ignoring batching
  trait SingleCustomTransformer[F[_]] extends CustomTransformer[F] {

    implicit def monad: Monad[F]

    final override def createTransformation(continuation: PartInterpreterType[F], context: CustomTransformerContext): PartInterpreterType[F] = {
      val singleTransformation = createSingleTransformation(continuation, context)
      _.map(singleTransformation).sequence.map(sequence)
    }

    def createSingleTransformation(continuation: PartInterpreterType[F], context: CustomTransformerContext): Context => F[ResultType[PartResult]]

  }

  //This is case where we don't want to affect invocation flow, just modify context
  trait MapWithStateCustomTransformer[F[_]] extends SingleCustomTransformer[F] {

    final override def createSingleTransformation(continuation: PartInterpreterType[F], context: CustomTransformerContext): Context => F[ResultType[PartResult]] = {
      val transformation = createStateTransformation(context)
      ctx => transformation(ctx).flatMap(newCtx => continuation(newCtx :: Nil))
    }

    def createStateTransformation(context: CustomTransformerContext): Context => F[Context]

  }

}
