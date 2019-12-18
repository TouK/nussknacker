package pl.touk.nussknacker.engine.process

import java.util.Date

import cats.data.NonEmptyList
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.PatientScalaFutures

class FlinkStreamingProcessRegistrarSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import spel.Implicits._

  it should "perform simple valid process" in {
    val process = EspProcess(MetaData("proc1", StreamMetaData()),
      ExceptionHandlerRef(List.empty),
      NonEmptyList.of(GraphBuilder.source("id", "input")
        .filter("filter1", "#processHelper.add(12, #processHelper.constant()) > 1")
        .filter("filter2", "#input.intAsAny + 1 > 1")
        .filter("filter3", "#input.value3Opt + 1 > 1")
        .filter("filter4", "#input.value3Opt.abs + 1 > 1")
        .filter("filter5", "#input.value3Opt.abs() + 1 > 1")
        .filter("filter6", "#input.value3.abs + 1 > 1")
        .processor("proc2", "logService", "all" -> "#input.value2")
        .emptySink("out", "monitor")))
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0), Option(1)),
      SimpleRecord("1", 15, "b", new Date(1000), None),
      SimpleRecord("2", 12, "c", new Date(2000), Option(3)),
      SimpleRecord("1", 23, "d", new Date(5000), Option(4))
    )

    processInvoker.invokeWithSampleData(process, data)

    eventually {
      MockService.data.toSet shouldBe Set("a", "c", "d")
    }
  }

  //TODO: some better check than "it does not crash"?
  it should "use rocksDB backend" in {
    val process = EspProcess(MetaData("proc1", StreamMetaData(splitStateToDisk = Some(true))),
      ExceptionHandlerRef(List.empty),
      NonEmptyList.of(GraphBuilder.source("id", "input")
        .customNode("custom2", "outRec", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'terefere'")
        .processor("proc2", "logService", "all" -> "#input.value2")
        .emptySink("out", "monitor")))
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0), Option(1)),
      SimpleRecord("1", 15, "b", new Date(1000), None),
      SimpleRecord("2", 12, "c", new Date(2000), Option(3))
    )

    processInvoker.invokeWithSampleData(process, data)

    eventually {
      MockService.data.toSet shouldBe Set("a", "b", "c")
    }
  }

}
