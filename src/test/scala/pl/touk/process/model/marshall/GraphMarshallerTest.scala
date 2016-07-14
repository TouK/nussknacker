package pl.touk.process.model.marshall

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.process.model.graph.node._
import pl.touk.process.model.graph.expression._


class GraphMarshallerTest extends FlatSpec with Matchers {

  it should "marshall and unmarshall to same process" in {

    val process = StartNode("a", Filter("b", "alamakota == 'true'", End("c")))

    val unmarshalled = GraphMarshaller.fromJson(GraphMarshaller.toJson(process)).toOption

    unmarshalled should equal (Some(process))
  }

}
