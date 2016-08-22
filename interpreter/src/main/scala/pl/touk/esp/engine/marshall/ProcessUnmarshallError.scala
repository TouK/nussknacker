package pl.touk.esp.engine.marshall

import pl.touk.esp.engine.{canonize, compile}


sealed trait ProcessUnmarshallError {

  def nodeIds: Set[String]

}

object ProcessUnmarshallError {

  case class ProcessJsonDecodeError(msg: String) extends ProcessUnmarshallError {
    override val nodeIds: Set[String] = Set.empty
  }

}

sealed trait ProcessValidationError extends ProcessUnmarshallError

object ProcessValidationError {

  case class ProcessUncanonizationError(nested: canonize.ProcessUncanonizationError)
    extends ProcessValidationError {

    override def nodeIds: Set[String] = nested.nodeIds

  }

  case class ProcessCompilationError(nested: compile.ProcessCompilationError)  extends ProcessValidationError {
    override def nodeIds: Set[String] = nested.nodeIds
  }

}
