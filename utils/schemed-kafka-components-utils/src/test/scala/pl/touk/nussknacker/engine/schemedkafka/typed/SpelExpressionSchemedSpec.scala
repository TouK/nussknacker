package pl.touk.nussknacker.engine.schemedkafka.typed

import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.json.{JsonSchemaBuilder, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Standard
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class SpelExpressionSchemedSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private implicit val nodeId: NodeId = NodeId("dumbId")

  private val parser = SpelExpressionParser.default(
    getClass.getClassLoader,
    ModelDefinitionBuilder.emptyExpressionConfig,
    new SimpleDictRegistry(Map.empty),
    enableSpelForceCompile = false,
    Standard,
    ClassDefinitionTestUtils.buildDefinitionForClasses(classOf[GenericRecord])
  )

  test("compute avro record's get type based on type of fields") {
    val schemaWithIntValuesOnly = SchemaBuilder
      .record("foo")
      .fields()
      .requiredInt("foo")
      .requiredInt("bar")
      .endRecord()
    val recordType = AvroSchemaTypeDefinitionExtractor.typeDefinition(schemaWithIntValuesOnly)
    val ctx = ValidationContext.empty
      .withVariable("avroRecord", recordType, None)
      .validValue

    val resultType = parser.parse("#avroRecord.get('foo')", ctx, Unknown).validValue.typingInfo.typingResult

    resultType shouldEqual Typed[Int]
  }

  test("compute json record's methods types based on type of fields") {
    val schemaWithStringValuesOnly = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    },
        |    "bar": {
        |      "type": "string"
        |    }
        |  }
        |}""".stripMargin)
    val recordType = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schemaWithStringValuesOnly).typingResult
    val ctx = ValidationContext.empty
      .withVariable("jsonRecord", recordType, None)
      .validValue

    val resultType = parser.parse("#jsonRecord.get('foo')", ctx, Unknown).validValue.typingInfo.typingResult

    resultType shouldEqual Typed[String]
  }

}
