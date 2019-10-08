package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.spel

class DictsSpec extends FunSuite with Matchers {

  import spel.Implicits._

  test("use dicts with static enum values") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .filter("filter", "#input.enumValue == #enum['ONE']")
      .processorEnd("proc2", "logService", "all" -> "#input")

    val data = List( 
      SimpleRecord("1", 3, "fooId", new Date(0), enumValue = SimpleJavaEnum.ONE),
      SimpleRecord("1", 5, "invalidId", new Date(1000), enumValue = SimpleJavaEnum.TWO))

    processInvoker.invoke(process, data)

    MockService.data should have size 1
  }

}
