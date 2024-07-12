package pl.touk.nussknacker.engine.process.registrar

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.Date

class FlinkStreamingProcessRegistrarSpec
    extends AnyFlatSpec
    with Matchers
    with ProcessTestHelpers
    with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  it should "perform simple valid process" in {
    val process = ScenarioBuilder
      .streaming("test")
      .source("id", "input")
      .filter("filter1", "#processHelper.add(12, #processHelper.constant()) > 1".spel)
      .filter("filter2", "#input.intAsAny + 1 > 1".spel)
      .filter("filter3", "#input.value3Opt + 1 > 1".spel)
      .filter("filter4", "#input.value3Opt.abs + 1 > 1".spel)
      .filter("filter5", "#input.value3Opt.abs() + 1 > 1".spel)
      .filter("filter6", "#input.value3.abs + 1 > 1".spel)
      .processor("proc2", "logService", "all" -> "#input.value2".spel)
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0), Option(1)),
      SimpleRecord("1", 15, "b", new Date(1000), None),
      SimpleRecord("2", 12, "c", new Date(2000), Option(3)),
      SimpleRecord("1", 23, "d", new Date(5000), Option(4))
    )

    processInvoker.invokeWithSampleData(process, data)
    ProcessTestHelpers.logServiceResultsHolder.results.toSet shouldBe Set("a", "c", "d")
  }

  // TODO: some better check than "it does not crash"?
  it should "use rocksDB backend" in {
    val process =
      ScenarioBuilder
        .streaming("test")
        .stateOnDisk(true)
        .source("id", "input")
        .customNode("custom2", "outRec", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'terefere'".spel)
        .processor("proc2", "logService", "all" -> "#input.value2".spel)
        .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0), Option(1)),
      SimpleRecord("1", 15, "b", new Date(1000), None),
      SimpleRecord("2", 12, "c", new Date(2000), Option(3))
    )

    processInvoker.invokeWithSampleData(process, data)
    ProcessTestHelpers.logServiceResultsHolder.results.toSet shouldBe Set("a", "b", "c")
  }

}
