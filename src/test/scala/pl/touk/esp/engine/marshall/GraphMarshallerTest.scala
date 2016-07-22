package pl.touk.esp.engine.marshall

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.expression._


class GraphMarshallerTest extends FlatSpec with Matchers {

  it should "marshall and unmarshall to same process" in {

    val process = StartNode("a", Filter("b", "alamakota == 'true'", End("c")))

    val unmarshalled = GraphMarshaller.fromJson(GraphMarshaller.toJson(process)).toOption

    unmarshalled should equal (Some(process))
  }

}
