package pl.touk.esp.engine.process

import java.util.Date

import org.scalatest.concurrent.Eventually
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.esp.engine.spel

class FlinkProcessRegistrarSpec extends FlatSpec with Matchers with Eventually {

  import spel.Implicits._

  it should "perform simple valid process" in {
    val process = EspProcess(MetaData("proc1"),
      ExceptionHandlerRef(List.empty),
      GraphBuilder.source("id", "input")
        .filter("filter1", "#processHelper.add(12, #processHelper.constant()) > 1")
        .filter("filter2", "#input.intAsAny + 1 > 1")
        .filter("filter3", "#input.value3Opt + 1 > 1")
        .filter("filter4", "#input.value3Opt.abs + 1 > 1")
        .filter("filter5", "#input.value3Opt.abs() + 1 > 1")
        .filter("filter6", "#input.value3.abs + 1 > 1")
        .processor("proc2", "logService", "all" -> "#input.value2")
        .sink("out", "monitor"))
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0), Option(1)),
      SimpleRecord("1", 15, "b", new Date(1000), None),
      SimpleRecord("2", 12, "c", new Date(2000), Option(3)),
      SimpleRecord("1", 23, "d", new Date(5000), Option(4))
    )

    processInvoker.invoke(process, data)

    eventually {
      MockService.data.toSet shouldBe Set("a", "c", "d")
    }
  }

  //TODO: jakies lepsze sprawdzenia niz "nie wywala sie"??
  it should "use rocksDB backend" in {
    val process = EspProcess(MetaData("proc1", splitStateToDisk = Some(true)),
      ExceptionHandlerRef(List.empty),
      GraphBuilder.source("id", "input")
        .customNode("custom2", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
        .processor("proc2", "logService", "all" -> "#input.value2")
        .sink("out", "monitor"))
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0), Option(1)),
      SimpleRecord("1", 15, "b", new Date(1000), None),
      SimpleRecord("2", 12, "c", new Date(2000), Option(3))
    )

    processInvoker.invoke(process, data)

    eventually {
      MockService.data.toSet shouldBe Set("a", "b", "c")
    }
  }

}
