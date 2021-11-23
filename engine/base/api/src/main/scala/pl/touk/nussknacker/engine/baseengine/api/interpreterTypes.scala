package pl.touk.nussknacker.engine.baseengine.api

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.ResultType

import scala.language.higherKinds

object interpreterTypes {

  case class SourceId(value: String)

  case class ScenarioInputBatch(value: List[(SourceId, Context)])

  case class EndResult[Result](nodeId: NodeId, context: Context, result: Result)

  //F represents effects (Future, State etc.), Result represnts specific output from Sink (e.g. in standalone engine)
  //TODO: can Result be represented as Writer[Result, Unit]??
  trait ScenarioInterpreter[F[_], Result] {

    def invoke(inputBatch: ScenarioInputBatch): F[ResultType[EndResult[Result]]]

    def sources: Map[SourceId, Source]

    def sinkTypes: Map[NodeId, TypingResult]

  }

}
