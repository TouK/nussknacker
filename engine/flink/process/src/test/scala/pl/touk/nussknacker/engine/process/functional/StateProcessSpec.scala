package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel

class StateProcessSpec extends FlatSpec with Matchers {

  //FIXME: ignored for now
  ignore should "fire alert when aggregate threshold exceeded" in {
    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 15, "b", new Date(1000)),
      SimpleRecord("1", 23, "d", new Date(4000))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    MockService.data(0) shouldBe Set("a", "b")
  }

}
