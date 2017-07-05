package pl.touk.esp.engine.api

import scala.concurrent.{ExecutionContext, Future}

/**
  * Hook for using Apache Flink API directly.
  * See examples in [[pl.touk.esp.engine.example.custom]]
**/
trait CustomStreamTransformer {

  //TODO: it is still pretty easy to forget about it. maybe shouldn't be default??
  //maybe should be detected somehow by types??
  def clearsContext : Boolean = false

}

trait LazyInterpreter[T] {

  def createInterpreter(ec: ExecutionContext) : (InterpretationResult => Future[T])

  def syncInterpretationFunction : InterpretationResult => T

}
