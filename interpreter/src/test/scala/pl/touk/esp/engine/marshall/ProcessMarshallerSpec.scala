package pl.touk.esp.engine.marshall

import argonaut.PrettyParams
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.engine._
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.sink.SinkRef

import scala.concurrent.duration._

class ProcessMarshallerSpec extends FlatSpec with Matchers with OptionValues {

  import spel.Implicits._

  it should "marshall and unmarshall to same process" in {

    def nestedGraph(id: String) =
      GraphBuilder
        .processor(id + "Processor", id + "Service")
        .sink(id + "End", "")

    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("a", "")
        .filter("b", "alamakota == 'true'", Some(nestedGraph("b")))
        .buildVariable("c", "fooVar", "f1" -> "expr1", "f2" -> "expr2")
        .enricher("d", "barVar", "dService", "p1" -> "expr3")
        .aggregate("e", "input", "alamakota == 'false'", 10000 milli, 5000 milli)
        .to(Switch("f", "expr4", "eVar", List(Case("e1", Sink("endE1", SinkRef("", List.empty)))), Some(nestedGraph("e"))))

    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)
    println(marshalled)

    val unmarshalled = ProcessMarshaller.fromJson(marshalled).toOption
    val result = ProcessCanonizer.uncanonize(unmarshalled.value).toOption

    result should equal(Some(process))
  }

}
