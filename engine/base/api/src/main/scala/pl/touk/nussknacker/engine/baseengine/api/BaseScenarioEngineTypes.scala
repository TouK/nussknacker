package pl.touk.nussknacker.engine.baseengine.api

import cats.data.Writer
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{Context, JoinReference, LazyParameter, LazyParameterInterpreter}

import scala.language.higherKinds

object BaseScenarioEngineTypes {

  //In the future we can add more stuff here
  type CustomTransformerContext = LazyParameterInterpreter

  type ErrorType = EspExceptionInfo[_ <: Throwable]

  //Errors are collected, we don't stop processing after encountering error
  type ResultType[T] = Writer[List[ErrorType], List[T]]

  //F is effect type of engine. Can be Future, IO, State or other monad
  //List[Context] instead of Context to allow using in some batch cases (where the batch is small enough to fit in the memory...)
  type PartInterpreterType[F[_]] = List[Context] => F[ResultType[PartResult]]

  sealed trait BaseCustomTransformer[F[_]] {
    type CustomTransformationOutput

    def createTransformation(continuation: PartInterpreterType[F],
                             context: CustomTransformerContext): CustomTransformationOutput
  }

  trait CustomTransformer[F[_]] extends BaseCustomTransformer[F] {
    type CustomTransformationOutput = PartInterpreterType[F]
  }

  trait JoinCustomTransformer[F[_]] extends BaseCustomTransformer[F] {
    //String here is the id of the branch
    type CustomTransformationOutput = List[(String, Context)] => F[ResultType[PartResult]]
  }

  //TODO: is this enough?
  trait BaseEngineSink[Res <: AnyRef] extends Sink {
    def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[Res]
  }

  case class SourceId(value: String)

  sealed trait PartResult

  case class EndResult[Result](nodeId: String, context: Context, result: Result) extends PartResult

  case class JoinResult(reference: JoinReference, context: Context) extends PartResult

}
