package pl.touk.nussknacker.engine.baseengine.api

import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{Context, JoinReference, LazyParameter, LazyParameterInterpreter}

import scala.language.higherKinds

object BaseScenarioEngineTypes {

  type CustomTransformerData = LazyParameterInterpreter

  type ErrorType = EspExceptionInfo[_ <: Throwable]

  type GenericResultType[T] = Either[ErrorType, T]

  type GenericListResultType[T] = List[GenericResultType[T]]

  type InterpretationResultType[Result] = GenericListResultType[EndResult[Result]]

  type InternalInterpreterOutputType[F[_]] = F[GenericListResultType[PartResultType]]

  type InterpreterType[F[_]] = List[Context] => InternalInterpreterOutputType[F]

  case class SourceId(value: String)

  sealed trait PartResultType

  case class EndResult[Result](nodeId: String, context: Context, result: Result) extends PartResultType

  case class JoinResult(reference: JoinReference, context: Context) extends PartResultType

  sealed trait BaseCustomTransformer {

    type CustomTransformation

    def createTransformation(outputVariable: Option[String]): CustomTransformation

  }

  trait CustomTransformer[F[_]] extends BaseCustomTransformer {

    type CustomTransformation = (InterpreterType[F], CustomTransformerData) => InterpreterType[F]

  }

  trait JoinCustomTransformer[F[_]] extends BaseCustomTransformer {

    type CustomTransformation = (InterpreterType[F], CustomTransformerData) => List[(String, Context)] => InternalInterpreterOutputType[F]

  }

  trait BaseEngineSink[Res<:AnyRef] extends Sink {

    def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[Res]

  }

}
