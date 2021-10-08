package pl.touk.nussknacker.engine.marshall


sealed trait ProcessUnmarshallError {

  def nodeIds: Set[String]

}

object ProcessUnmarshallError {

  case class ProcessJsonDecodeError(msg: String) extends ProcessUnmarshallError {
    override val nodeIds: Set[String] = Set.empty
  }

}

