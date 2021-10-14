package pl.touk.nussknacker.engine.baseengine.api

import cats.data.Writer
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{Context, JoinReference, LazyParameter, LazyParameterInterpreter}

import scala.language.higherKinds

object BaseScenarioEngineTypes {

  type CustomTransformerContext = LazyParameterInterpreter

  type ErrorType = EspExceptionInfo[_ <: Throwable]

  //Errors are collected, we don't stop processing after encountering error
  type ResultType[T] = Writer[List[ErrorType], List[T]]

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
    type CustomTransformationOutput = List[(String, Context)] => F[ResultType[PartResult]]
  }

  trait BaseEngineSink[Res <: AnyRef] extends Sink {
    def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[Res]
  }

  sealed trait PartResult

  case class SourceId(value: String)

  case class EndResult[Result](nodeId: String, context: Context, result: Result) extends PartResult

  case class JoinResult(reference: JoinReference, context: Context) extends PartResult

  implicit class GenericListResultTypeOps[T](self: ResultType[T]) {
    def add(other: ResultType[T]): ResultType[T] = self.bimap(_ ++ other.run._1, _ ++ other.run._2)
  }

}
