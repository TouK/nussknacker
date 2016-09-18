package pl.touk.esp.engine.api

sealed trait InterpreterMode

object InterpreterMode {

  case object Traverse extends InterpreterMode
  case object AggregateKeyExpression extends InterpreterMode
  case object AggregateTriggerExpression extends InterpreterMode
  case class CustomNodeExpression(name: String) extends InterpreterMode

}
