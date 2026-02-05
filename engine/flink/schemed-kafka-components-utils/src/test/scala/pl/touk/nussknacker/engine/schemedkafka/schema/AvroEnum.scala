package pl.touk.nussknacker.engine.schemedkafka.schema

object AvroEnum {

  object V1 extends TestSchemaWithRecord {

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

    override def exampleData: Map[String, Any] = Map("testField" -> "GREEN")
  }

  object V2 extends TestSchemaWithRecord {

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
          |      "default": null
          |    }
          |  ]
          |}""".stripMargin

    override def exampleData: Map[String, Any] = Map("testField" -> "RED")

  }

  object V3 extends TestSchemaWithRecord {

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
          |      "default": null
          |    }
          |  ]
          |}""".stripMargin

    override def exampleData: Map[String, Any] = Map("testField" -> "BLUE")

  }

}
