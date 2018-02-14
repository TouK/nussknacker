package pl.touk.nussknacker.ui.codec

import java.time.LocalDateTime

import argonaut.Parse
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.Displayable
import pl.touk.nussknacker.engine.api.deployment.test.{NodeResult, TestResults}

class UiCodecsSpec extends FlatSpec with Matchers {


  it should "should encode record" in {

    val date = LocalDateTime.of(2010, 1, 1, 1, 1)
    val ctx = api.Context("terefere").withVariables(Map(
        "var1" -> TestRecord("a", 1, Some("b"), date),
        "var2" -> CsvRecord(List("aa", "bb"))
      ))

    val testResults = TestResults(Map("n1" -> List(NodeResult(ctx))), Map(), Map(), List())

    val json = UiCodecs.testResultsEncoder.encode(testResults)
    
    val variables = (for {
      nodeResults <- json.cursor --\ "nodeResults"
      n1Results <- nodeResults --\ "n1"
      firstResult <- n1Results.\\
      ctx <- firstResult --\ "context"
      vars <- ctx --\ "variables"
      var1 <- vars --\ "var1"
      var2 <- vars --\ "var2"
    } yield List(var1.focus, var2.focus)).toList.flatten


    variables.size shouldBe 2
    //how to make it prettier?
    variables(0) shouldBe Parse.parse("{\"pretty\":\"{\\n  \\\"date\\\" : \\\"2010-01-01T01:01\\\",\\n  \\\"some\\\" : \\\"b\\\",\\n  \\\"number\\\" : 1,\\n  \\\"id\\\" : \\\"a\\\"\\n}\"}").right.get
    variables(1) shouldBe Parse.parse("{\"original\":\"aa|bb\",\"pretty\":\"{\\n  \\\"fieldA\\\" : \\\"aa\\\",\\n  \\\"fieldB\\\" : \\\"bb\\\"\\n}\"}").right.get
  }

  import UiCodecs._
  case class TestRecord(id: String, number: Long, some: Option[String], date: LocalDateTime) extends Displayable {
    override def originalDisplay: Option[String] = None
    override def display = this.asJson
  }

  case class CsvRecord(fields: List[String]) extends Displayable {

    override def originalDisplay: Option[String] = Some(fields.mkString("|"))

    override def display = {
      jObjectFields(
        "fieldA" -> jString(fieldA),
        "fieldB" -> jString(fieldB)
      )
    }
    val fieldA = fields(0)
    val fieldB = fields(1)
  }

}

