package pl.touk.nussknacker.engine.api.deployment

import io.circe.parser.parse

object GraphProcess {

  //TODO: Add json validation here?
  def apply(jsonString: String): GraphProcess = {
    val json = parse(jsonString) match {
      case Left(_) => throw new IllegalArgumentException(s"Invalid raw json string: $jsonString.")
      case Right(json) => json.spaces2
    }

    new GraphProcess(json)
  }

}

final case class GraphProcess private(jsonString: String) extends AnyVal
