package pl.touk.nussknacker.engine.requestresponse.test

import cats.data.Validated.Valid
import com.typesafe.config.ConfigFactory
import org.scalatest.Inside.inside
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.requestresponse.{FutureBasedRequestResponseScenarioInterpreter, Request1, RequestNumber, RequestResponseConfigCreator, Response}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.charset.StandardCharsets
import scala.collection.convert.Wrappers.SeqWrapper

class RequestResponseCollectorSpec extends FunSuite with Matchers with BeforeAndAfterEach with RunnableEspScenario {

  import pl.touk.nussknacker.engine.spel.Implicits._
  implicit private val modelData: LocalModelData = LocalModelData(ConfigFactory.load(), new RequestResponseConfigCreator)

  test("Collect after for-each") {

    val numberOfElements = 6

    val scenario = ScenarioBuilder
      .requestResponse("proc")
      .source("start", "request-list-post-source")
      .customNode("for-each", "outForEach", "for-each", "Elements" -> "#input.toList()")
      .buildSimpleVariable("someVar", "ourVar", """ "x = " + (#outForEach * 2) """)
      .customNode("collector", "outCollector", "collector", "toCollect" -> "#ourVar", "maxCount" -> "")
      .emptySink("sink", "response-sink", "value" -> "#outCollector")


    val resultE = scenario.runTestWith(RequestNumber(numberOfElements))
    resultE shouldBe 'valid
    val result = resultE.map(_.asInstanceOf[List[Any]]).getOrElse(throw new AssertionError())
    val validElementList = (0 to numberOfElements).map(s => s"x = ${s * 2}").toSeq
    result should have length 1

    inside(result.head) {
      case resp: SeqWrapper[_] => resp.underlying should contain allElementsOf(validElementList)
    }

  }

}
