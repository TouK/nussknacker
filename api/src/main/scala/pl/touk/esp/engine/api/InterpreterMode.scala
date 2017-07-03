package pl.touk.esp.engine.api

sealed trait InterpreterMode

object InterpreterMode {

  case object Traverse extends InterpreterMode
  case class CustomNodeExpression(name: String) extends InterpreterMode

}
