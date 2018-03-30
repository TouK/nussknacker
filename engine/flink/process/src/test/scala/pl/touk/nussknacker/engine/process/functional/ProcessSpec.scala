package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, SimpleRecordWithPreviousValue, processInvoker}
import pl.touk.nussknacker.engine.spel

class ProcessSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "skip null records" in {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processorEnd("proc2", "logService", "all" -> "#input")


    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      null,
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      null,
      SimpleRecord("1", 20, "d", new Date(10000))

    )

    processInvoker.invoke(process, data)

    MockService.data should have size 5

  }
}
