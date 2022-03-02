package pl.touk.nussknacker.engine

import io.circe.syntax._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.ProcessNodesRewriter
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import scala.language.implicitConversions

class ScenarioApiShowcasesTest extends FunSuite with Matchers with EitherValuesDetailedMessage {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private val scenarioId = "fooId"
  private val sourceNodeId = "source"
  private val sourceType = "source-type"

  private val scenarioJson =
    s"""{
       |  "metaData" : {
       |    "id" : "$scenarioId",
       |    "typeSpecificData" : {
       |      "spillStateToDisk" : true,
       |      "type" : "StreamMetaData"
       |    },
       |    "subprocessVersions" : {
       |      
       |    }
       |  },
       |  "nodes" : [
       |    {
       |      "id" : "$sourceNodeId",
       |      "ref" : {
       |        "typ" : "$sourceType",
       |        "parameters" : [
       |          {
       |            "name" : "foo",
       |            "expression" : {
       |              "language" : "spel",
       |              "expression" : "'expression value'"
       |            }
       |          }
       |        ]
       |      },
       |      "type" : "Source"
       |    },
       |    {
       |      "nextFalse" : [
       |      ],
       |      "id" : "filter",
       |      "expression" : {
       |        "language" : "spel",
       |        "expression" : "#input != 123"
       |      },
       |      "type" : "Filter"
       |    },
       |    {
       |      "id" : "sink",
       |      "ref" : {
       |        "typ" : "sink-type",
       |        "parameters" : [
       |          {
       |            "name" : "bar",
       |            "expression" : {
       |              "language" : "spel",
       |              "expression" : "#input"
       |            }
       |          }
       |        ]
       |      },
       |      "type" : "Sink"
       |    }
       |  ],
       |  "additionalBranches" : [
       |  ]
       |}""".stripMargin

  test("should be able to parse scenario and easily extract its fields") {
    val canonicalScenario = ProcessMarshaller.fromJson(scenarioJson).toEither.rightValue
    canonicalScenario.metaData.id shouldEqual scenarioId
    canonicalScenario.nodes.head.data.id shouldEqual sourceNodeId
    canonicalScenario.nodes.head.data.asInstanceOf[Source].ref.typ shouldEqual sourceType
  }

  test("should be able to create scenario using dsl and print it") {
    val scenarioDsl = EspProcessBuilder
      .id(scenarioId)
      .source(sourceNodeId, sourceType, "foo" -> "'expression value'")
      .filter("filter", "#input != 123")
      .emptySink("sink", "sink-type", "bar" -> "#input")

    scenarioDsl.toCanonicalProcess.asJson.printWith(humanReadablePrinter) shouldEqual scenarioJson
  }

  test("should be able to rewrite scenario") {
    val canonicalScenario = ProcessMarshaller.fromJson(scenarioJson).toEither.rightValue
    val rewritten = ProcessNodesRewriter.rewritingAllExpressions(_ => expr => expr.copy(language = "fooLang")).rewriteProcess(canonicalScenario)
    rewritten.nodes.head.data.asInstanceOf[Source].parameters.head.expression.language shouldEqual "fooLang"
  }

}
