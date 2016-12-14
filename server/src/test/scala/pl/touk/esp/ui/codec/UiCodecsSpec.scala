package pl.touk.esp.ui.codec

import java.time.LocalDateTime

import argonaut.Argonaut._
import argonaut._
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api
import pl.touk.esp.engine.definition.DefinitionExtractor.TypesInformation

class UiCodecsSpec extends FlatSpec with Matchers {

  it should "should encode record" in {

    val codec = UiCodecs.ContextCodecs(TypesInformation.extract(List(), List(), List(), List(classOf[TestRecord])))
    import codec._

    val json =
      api.Context(Map("var1" ->
        TestRecord("a", 1, Some("b"), LocalDateTime.now(), Some(TestRecord("c", 2, None, LocalDateTime.now(), None)))
      )).asJson

    for {
      var1 <- json.cursor --\ "var1"
      id <- var1 --\ "id"
      inner <- var1 --\ "record"
      innerNumber <- inner --\ "number"
      some <- var1 --\ "some"
      innerSome = inner --\ "some"

    } yield {
      id.focus shouldBe jString("a")
      some.focus shouldBe jString("b")
      innerNumber.focus shouldBe jNumber(2)
      innerSome shouldBe 'empty
    }


  }

  case class TestRecord(id: String, number: Long, some: Option[String], date: LocalDateTime, record: Option[TestRecord])

}

