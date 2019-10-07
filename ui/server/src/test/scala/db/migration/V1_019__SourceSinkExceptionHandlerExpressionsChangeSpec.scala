package db.migration

import argonaut.{Json, Parse}
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Sink, Source}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class V1_019__SourceSinkExceptionHandlerExpressionsChangeSpec extends FlatSpec with Matchers {

  private val migrationFunc = V1_019__SourceSinkExceptionHandlerExpressionsChange.processJson _

  private val meta = """"metaData":{"id":"DEFGH","typeSpecificData": {"type": "StreamMetaData", "parallelism" : 4}, "additionalFields":{"groups":[]}}"""

  private val sourceToConvert = """  {
       |      "type" : "Source",
       |      "id" : "start",
       |      "ref" : {
       |        "typ" : "source1",
       |        "parameters" : [
       |          {
       |            "name" : "param1",
       |            "value" : "string1"
       |          }
       |        ]
       |      }
       |  }"""

  private def sinkToConvert(id: String) = s"""{
      |          "type" : "Sink",
      |          "id" : "$id",
      |          "ref" : {
      |            "typ" : "sink",
      |            "parameters" : [
      |              {
      |                "name" : "param1",
      |                "value" : "string1"
      |              }
      |            ]
      |          }
      |       }"""

  private implicit val marshaller = ProcessMarshaller

  it should "convert exceptionHandlerRef" in {

    val oldJson =
      Parse.parse(
        s"""{
          |$meta,
          |"exceptionHandlerRef": {"parameters":[{"name": "param1", "value": "string1"}]},
          |"nodes":[
          |]}
          |""".stripMargin).right.get

    val converted = migrateAndConvert(oldJson)
    val handler = converted.exceptionHandlerRef
            handler shouldBe ExceptionHandlerRef(List(Parameter("param1", Expression("spel", "'string1'"))))
  }

  it should "convert source" in {

    val oldJson =
      Parse.parse(
        s"""{
          |$meta,
          |"exceptionHandlerRef": {"parameters":[]},
          |"nodes":[
          |    $sourceToConvert
          |  ]
          |}
          |""".stripMargin).right.get

    val migrated = migrationFunc(oldJson).get

    val converted = migrateAndConvert(oldJson)
    val source = converted.nodes.head.asInstanceOf[FlatNode].data.asInstanceOf[Source]

    source shouldBe Source("start", SourceRef("source1", List(Parameter("param1", Expression("spel", "'string1'")))))
  }

  it should "convert sink in filter false" in {

    val oldJson =
      Parse.parse(
        s"""{
          |$meta,
          |"exceptionHandlerRef": {"parameters":[]},
          |"nodes":[
          |  $sourceToConvert,
          |  {
          |    "type" : "Filter",
          |    "id" : "filter1",
          |    "expression" : {
          |      "language" : "spel",
          |      "expression" : "true"
          |    },
          |    "nextFalse" : [
          |      ${sinkToConvert("sink1")}
          |    ]
          |  },
          |  ${sinkToConvert("sink2")}
          |  ]
          |}
          |""".stripMargin).right.get

    val converted = migrateAndConvert(oldJson)

    val sink1 = converted.nodes(1).asInstanceOf[FilterNode].nextFalse.head.data.asInstanceOf[Sink]
    sink1 shouldBe sinkToVerify("sink1")

    val sink2 = converted.nodes.last.data.asInstanceOf[Sink]
    sink2 shouldBe sinkToVerify("sink2")
  }

  it should "convert sink in split" in {

    val oldJson =
      Parse.parse(
        s"""{
          |$meta,
          |"exceptionHandlerRef": {"parameters":[]},
          |"nodes":[
          |  $sourceToConvert,
          |  {
          |    "type" : "Split",
          |    "id" : "split",
          |
          |    "nexts" : [
          |      [ ${sinkToConvert("sink1")}],
          |      [ ${sinkToConvert("sink2")}]
          |    ]
          |  }
          |  ]
          |}
          |""".stripMargin).right.get

    val converted = migrateAndConvert(oldJson)

    val nexts = converted.nodes(1).asInstanceOf[SplitNode].nexts

    nexts(0).head.data shouldBe sinkToVerify("sink1")

    nexts(1).head.data shouldBe sinkToVerify("sink2")
  }

  it should "convert sink in switch" in {

    val oldJson =
      Parse.parse(
        s"""{
          |$meta,
          |"exceptionHandlerRef": {"parameters":[]},
          |"nodes":[
          |  $sourceToConvert,
          |  {
          |    "type" : "Switch",
          |    "id" : "switch",
          |    "expression" : {
          |      "language" : "spel",
          |      "expression" : "true"
          |    },
          |    "exprVal": "val",
          |    "nexts": [
          |     {
          |       "expression" : {
          |         "language" : "spel",
          |         "expression" : "true"
          |       },
          |       "nodes": [
          |         ${sinkToConvert("sink1")}
          |       ]
          |     }
          |    ],
          |    "defaultNext" : [
          |      ${sinkToConvert("sink2")}
          |    ]
          |  }
          |  ]
          |}
          |""".stripMargin).right.get

    val converted = migrateAndConvert(oldJson)

    val switch = converted.nodes(1).asInstanceOf[SwitchNode]


    val sink1 = switch.nexts.head.nodes.head.data
    sink1 shouldBe sinkToVerify("sink1")

    val defaultNext = switch.defaultNext
    defaultNext.head.data shouldBe sinkToVerify("sink2")

  }

  it should "convert sink in subprocess" in {

    val oldJson =
      Parse.parse(
        s"""{
          |$meta,
          |"exceptionHandlerRef": {"parameters":[]},
          |"nodes":[
          |  $sourceToConvert,
          |  {
          |    "type" : "SubprocessInput",
          |    "id" : "subprocess",
          |    "ref" : {
          |      "id" : "subprocess1",
          |      "parameters" : []
          |    },
          |    "outputs" : {
          |      "output1": [ ${sinkToConvert("sink1")} ]
          |    }
          |  }
          |  ]
          |}
          |""".stripMargin).right.get

    val converted = migrateAndConvert(oldJson)
    
    val sink1 = converted.nodes(1).asInstanceOf[Subprocess].outputs("output1").head.data.asInstanceOf[Sink]
    sink1 shouldBe sinkToVerify("sink1")

  }


  private def sinkToVerify(id: String) = {
    Sink(id, SinkRef("sink", List(Parameter("param1", Expression("spel", "'string1'")))))
  }

  private def migrateAndConvert(oldJson: Json) : CanonicalProcess = {
    val migrated = migrationFunc(oldJson).get
    
    marshaller.fromJson(migrated.nospaces) match {
      case Invalid(errors) => throw new AssertionError(errors)
      case Valid(converted) => converted
    }
  }
}
