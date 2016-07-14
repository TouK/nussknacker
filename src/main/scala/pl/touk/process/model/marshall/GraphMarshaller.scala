package pl.touk.process.model.marshall

import cats.data.Xor
import io.circe.Error
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import pl.touk.process.model.graph.node.Node

object GraphMarshaller {

  def toJson(node: Node) : String = {
    node.asJson.spaces2
  }

  def fromJson(value: String): Xor[Error, Node] = {
    //validation
    decode[Node](value)
  }

}

