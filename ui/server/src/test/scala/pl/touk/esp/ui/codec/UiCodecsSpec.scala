package pl.touk.esp.ui.codec

import java.time.LocalDateTime

import argonaut.Parse
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api
import pl.touk.esp.engine.api.Displayable
import pl.touk.esp.engine.definition.DefinitionExtractor.TypesInformation

class UiCodecsSpec extends FlatSpec with Matchers {

  import UiCodecs._

  it should "should encode record" in {

    val codec = UiCodecs.ContextCodecs(TypesInformation.extract(List(), List(), List(), List(), List(classOf[TestRecord])))
    import codec._

    val date = LocalDateTime.of(2010, 1, 1, 1, 1)
    val json =
      api.Context("terefere", Map(
        "var1" -> TestRecord("a", 1, Some("b"), date),
        "var2" -> CsvRecord(List("aa", "bb"))
      )).asJson

    val variables = (for {
      ctxId <- json.cursor --\ "id"
      vars <- json.cursor --\ "variables"
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

