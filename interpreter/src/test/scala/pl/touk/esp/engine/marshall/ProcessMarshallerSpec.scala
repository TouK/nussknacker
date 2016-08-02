package pl.touk.esp.engine.marshall

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.{Parameter, ServiceRef}
import pl.touk.esp.engine.graph.variable.Field

import scala.concurrent.duration._


class ProcessMarshallerSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "marshall and unmarshall to same process" in {

    def nestedGraph(id: String) =
      GraphBuilder
        .processor(id + "Processor", ServiceRef(id + "Service", List.empty))
        .sink(id + "End", "")

    val graph =
      GraphBuilder
        .source("a", "")
        .filter("b", "alamakota == 'true'", Some(nestedGraph("b")))
        .buildVariable("c", "fooVar", Field("f1", "expr1"), Field("f2", "expr2"))
        .enricher("d", ServiceRef("dService", List(Parameter("p1", "expr3"))), "barVar")
        .aggregate("e", "alamakota == 'false'", 10000 milli, 5000 milli)
        .to(Switch("f", "expr4", "eVar", List(Case("e1", Sink("endE1", SinkRef("", List.empty)))), Some(nestedGraph("e"))))

    val process = EspProcess(MetaData("process1"), graph)

    val marshalled = ProcessMarshaller.toJson(process)
    println(marshalled)

    val unmarshalled = ProcessMarshaller.fromJson(marshalled).toOption

    unmarshalled should equal(Some(process))
  }

}
