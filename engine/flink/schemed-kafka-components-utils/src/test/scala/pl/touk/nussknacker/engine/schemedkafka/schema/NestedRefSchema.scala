package pl.touk.nussknacker.engine.schemedkafka.schema

object NestedRefSchema {

  def jsonSchemaWithRefs: String = """{
    |  "type": "object",
    |  "$defs": {
    |    "ForwardCallRecord": {
    |      "anyOf": [
    |        {
    |          "type": "object",
    |          "properties": {
    |            "callDuration": {
    |              "type": "integer"
    |            }
    |          }
    |        },
    |        {
    |          "type": "null"
    |        }
    |      ],
    |      "default": null
    |    },
    |    "MtSMSRecord": {
    |      "anyOf": [
    |        {
    |          "type": "object",
    |          "properties": {
    |            "chargedParty": {
    |              "type": "integer"
    |            }
    |          }
    |        },
    |        {
    |          "type": "null"
    |        }
    |      ],
    |      "default": null
    |    }
    |  },
    |  "properties": {
    |    "forwardCallRecord": {
    |      "$ref": "#/$defs/ForwardCallRecord"
    |    },
    |    "mtSMSRecord": {
    |      "$ref": "#/$defs/MtSMSRecord"
    |    }
    |  }
    |}""".stripMargin

  def jsonSchema: String = """{
    |  "type": "object",
    |  "properties": {
    |    "forwardCallRecord": {
    |      "default": null,
    |      "anyOf": [
    |        {
    |          "type": "object",
    |          "properties": {
    |            "callDuration": {
    |              "type": "integer"
    |            }
    |          }
    |        },
    |        {
    |          "type": "null"
    |        }
    |      ]
    |    },
    |    "mtSMSRecord": {
    |      "default": null,
    |      "anyOf": [
    |        {
    |          "type": "object",
    |          "properties": {
    |            "chargedParty": {
    |              "type": "integer"
    |            }
    |          }
    |        },
    |        {
    |          "type": "null"
    |        }
    |      ]
    |    }
    |  }
    |}""".stripMargin

  val example1: Map[String, Any] = Map.empty

  val example2: Map[String, Any] = Map("forwardCallRecord" -> Map("callDuration" -> 123))

  val example3: Map[String, Any] = Map("mtSMSRecord" -> Map("chargedParty" -> 321))

  val example4: Map[String, Any] = Map(
    "forwardCallRecord" -> Map("callDuration" -> 123),
    "mtSMSRecord"       -> Map("chargedParty" -> 321)
  )

}
