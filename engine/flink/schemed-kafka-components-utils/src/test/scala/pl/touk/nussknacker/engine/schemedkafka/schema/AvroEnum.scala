package pl.touk.nussknacker.engine.schemedkafka.schema

object AvroEnum {

  object V1 extends TestSchema {

    override def stringSchema: String =
      s"""|{
          |  "type": "record",
          |  "name": "EnumTest",
          |  "namespace": "com.example",
          |  "fields": [
          |    {
          |      "name": "testField",
          |      "type": {
          |        "type": "enum",
          |        "name": "Color",
          |        "symbols": [
          |          "RED",
          |          "GREEN",
          |          "BLUE"
          |        ]
          |      },
          |      "default": "BLUE"
          |    }
          |  ]
          |}""".stripMargin

  }

  object V2 extends TestSchema {

    override def stringSchema: String =
      s"""|{
          |  "type": "record",
          |  "name": "EnumTest",
          |  "namespace": "com.example",
          |  "fields": [
          |    {
          |      "name": "testField",
          |      "type": [
          |        "null",
          |        {
          |          "type": "enum",
          |          "name": "Color",
          |          "symbols": [
          |            "RED",
          |            "GREEN",
          |            "BLUE"
          |          ]
          |        }
          |      ],
          |      "default": "null"
          |    }
          |  ]
          |}""".stripMargin

  }

  object V3 extends TestSchema {

    override def stringSchema: String =
      s"""|{
          |  "type": "record",
          |  "name": "EnumTest",
          |  "namespace": "com.example",
          |  "fields": [
          |    {
          |      "name": "testField",
          |      "type": [
          |        "null",
          |        {
          |          "type": "enum",
          |          "name": "Color",
          |          "symbols": [
          |            "RED",
          |            "GREEN",
          |            "BLUE"
          |          ]
          |        },
          |        "int"
          |      ],
          |      "default": "null"
          |    }
          |  ]
          |}""".stripMargin

  }

}
