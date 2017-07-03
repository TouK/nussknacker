package pl.touk.esp.engine.api

import scala.concurrent.{ExecutionContext, Future}

trait CustomStreamTransformer {

  //TODO: it is still pretty easy to forget about it. maybe shouldn't be default??
  //maybe should be detected somehow by types??
  def clearsContext : Boolean = false

}

trait LazyInterpreter[T] {

  def createInterpreter(ec: ExecutionContext) : (InterpretationResult => Future[T])

  def syncInterpretationFunction : InterpretationResult => T

}
