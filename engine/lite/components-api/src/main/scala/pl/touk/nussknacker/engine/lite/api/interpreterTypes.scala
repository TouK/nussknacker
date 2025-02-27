package pl.touk.nussknacker.engine.lite.api

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType

import scala.language.higherKinds

object interpreterTypes {

  case class SourceId(value: String)

  case class ScenarioInputBatch[Input](value: List[(SourceId, Input)])

  case class EndResult[Result](nodeId: NodeId, context: Context, result: Result)

  // F represents effects (Future, State etc.), Input represents input data type,
  // Result represents specific output from Sink (e.g. in request-response engine)
  // TODO: can Result be represented as Writer[Result, Unit]??
  trait ScenarioInterpreter[F[_], Input, Result] {

    def invoke(inputBatch: ScenarioInputBatch[Input]): F[ResultType[EndResult[Result]]]

    def sources: Map[SourceId, Source]

    def sinkTypes: Map[NodeId, TypingResult]

  }

}
