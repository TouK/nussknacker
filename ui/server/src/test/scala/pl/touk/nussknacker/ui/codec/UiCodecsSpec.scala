package pl.touk.nussknacker.ui.codec

import java.time.LocalDateTime
import java.util

import argonaut.Json._
import argonaut._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestResults
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.{Displayable, StreamMetaData}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData, Processor, SubprocessInputDefinition}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.definition.{NodeGroup, NodeToAdd, UIProcessDefinition, UIProcessObjects}

class UiCodecsSpec extends FunSuite with Matchers {

  test("should encode record") {

    val date = LocalDateTime.of(2010, 1, 1, 1, 1)
    val ctx = api.Context("terefere").withVariables(Map(
        "var1" -> TestRecord("a", 1, Some("b"), date),
        "var2" -> CsvRecord(List("aa", "bb"))
      ))

    val testResults = TestResults[Json](Map(), Map(), Map(), List(), UiCodecs.testResultsVariableEncoder)
      .updateNodeResult("n1", ctx)

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
    variables(0) shouldBe Parse.parse("{\"pretty\":{\"id\":\"a\",\"number\":1,\"some\":\"b\",\"date\":\"2010-01-01T01:01\"}}").right.get
    variables(1) shouldBe Parse.parse("{\"original\":\"aa|bb\",\"pretty\":{\"fieldA\":\"aa\",\"fieldB\":\"bb\"}}").right.get
  }

  test("encode Displayable with original but only on first level") {
    UiCodecs.testResultsVariableEncoder("abcd") shouldBe  jObjectFields("pretty" -> jString("abcd"))

    val csvRecord1 = CsvRecord(List("a", "b"))

    UiCodecs.testResultsVariableEncoder(csvRecord1) shouldBe  jObjectFields(
      "pretty" -> csvRecord1.display,
      "original" -> jString(csvRecord1.originalDisplay.get))

    val csvRecord2 = CsvRecord(List("c", "d"))

    UiCodecs.testResultsVariableEncoder(util.Arrays.asList(csvRecord1, csvRecord2)) shouldBe jObjectFields(
      "pretty" -> jArray(List(csvRecord1.display, csvRecord2.display)))

  }

  test("encode process objects with correct node data") {

    val encoded = UiCodecs.processObjectsEncode.encode(UIProcessObjects(
      List(NodeGroup(
        "base", List(NodeToAdd(
          "typ1", "label1", CustomNode("id", Some("output"), "typ1", List(Parameter("par1", Expression("spel", "aaa")))), List("trala")
        ))
      )),
      UIProcessDefinition(
        Map(),
        Map(),
        Map(),
        Map(),
        Map(),
        ObjectDefinition(List(), ClazzRef[String], List()),
        Map(),
        List(),
        Map()
      ),
      Map(),
      Map(),
      List()
    ))

    val customNode = (for {
      nodesToAdd <- encoded.cursor --\ "nodesToAdd"
      nodeArray <- nodesToAdd.\\
      group <- nodeArray --\ "possibleNodes"
      node <- group.\\
      nodeData <- node --\ "node"
    } yield nodeData).get.focus

    customNode shouldBe jObjectFields(
          "type" -> jString("CustomNode"),
          "id" -> jString("id"),
          "outputVar" -> jString("output"),
          "nodeType" -> jString("typ1"),
          "parameters" -> jArray(List(jObjectFields(
            "name" -> jString("par1"),
            "expression" -> jObjectFields("language" -> jString("spel"), "expression" -> jString("aaa"))
          )))
    )
  }

  test("decode process objects with correct node data") {
    import UiCodecs._

    val node = """
        |{
        |  "service": {
        |    "parameters": [
        |      {
        |        "expression": {
        |          "expression": "12",
        |          "language": "spel"
        |        },
        |        "name": "p1"
        |      }
        |    ],
        |    "id": "service1"
        |  },
        |  "id": "t1",
        |  "type": "Processor"
        |}
      """.stripMargin

    node.decodeOption[NodeData] shouldBe Some(Processor("t1", ServiceRef("service1", List(Parameter("p1", Expression("spel", "12"))))))
  }

  test("displayable process encode and decode") {
    import UiCodecs._

    val process = DisplayableProcess("", ProcessProperties(
      StreamMetaData(), ExceptionHandlerRef(List()),
      false,
      Some(ProcessAdditionalFields(Some("a"), Set(), Map("field1" -> "value1"))), Map()
    ), List(
      SubprocessInputDefinition("proc1", List(SubprocessParameter("param1", SubprocessClazzRef[String]))),
      CustomNode("id", Some("out1"), "typ1", List(Parameter("name1", Expression("spel", "11"))),
        Some(NodeAdditionalFields(Some("desc"))))
    ), List(

    ), "")

    val encoded = process.asJson

    encoded.spaces2.decodeOption[DisplayableProcess] shouldBe Some(process)
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

