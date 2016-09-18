package pl.touk.esp.engine.api

import scala.concurrent.{ExecutionContext, Future}

trait CustomStreamTransformer {


}

trait LazyInterpreter {
  def createInterpreter(ec: ExecutionContext) : (InterpretationResult => Future[InterpretationResult])

  def syncInterpretationFunction : InterpretationResult => InterpretationResult
}
