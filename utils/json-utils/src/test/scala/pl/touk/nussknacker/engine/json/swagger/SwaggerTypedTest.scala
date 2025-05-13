package pl.touk.nussknacker.engine.json.swagger

import io.swagger.v3.oas.models.media.{IntegerSchema, ObjectSchema, Schema}
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

class SwaggerTypedTest extends AnyFunSuite {

  test("should map int32 and int64 integer formats") {
    val schema = new ObjectSchema()
      .addProperty("int32", new IntegerSchema().format("int32"))
      .addProperty("int64", new IntegerSchema().format("int64"))
      .addProperty("integer", new IntegerSchema().format(null))

    val result = SwaggerTyped(schema, Map.empty).typingResult

    result shouldBe Typed.record(
      Map("int32" -> Typed[Integer], "int64" -> Typed[java.lang.Long], "integer" -> Typed[java.lang.Long])
    )
  }

}
