package pl.touk.nussknacker.engine.api

sealed trait InterpreterMode

object InterpreterMode {

  case object Traverse extends InterpreterMode
  case class CustomNodeExpression(name: String) extends InterpreterMode

}
