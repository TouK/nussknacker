package pl.touk.esp.engine.api

import scala.concurrent.{ExecutionContext, Future}

trait CustomStreamTransformer {


}

trait LazyInterpreter[T] {

  def createInterpreter(ec: ExecutionContext) : (InterpretationResult => Future[T])

  def syncInterpretationFunction : InterpretationResult => T

}
