package pl.touk.nussknacker.engine.avro

import cats.data.ValidatedNel
import io.circe.Json
import org.apache.avro.SchemaBuilder
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.avro.AvroDefaultExpressionExtractor.AvroDefaultToSpELExpressionError
import pl.touk.nussknacker.engine.graph.expression.Expression

class AvroDefaultExpressionExtractorTest extends FunSuite with Matchers {
  import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

  test("string default") {
    val schema = SchemaBuilder.builder().stringType()
    val defaultValue = json("\"a\"")
    val expression = new AvroDefaultExpressionExtractor(schema, defaultValue, handleNotSupported = false).toExpression
    verify(expression) {
      _ shouldBe Some(asSpelExpression("'a'"))
    }
  }

  private def verify(validatedExpression: ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]])(assertion: Option[Expression] => Assertion): Unit = {
    val expression = validatedExpression.valueOr(errors => throw errors.head)
    assertion(expression)
  }

  private def json(str: String): Json = {
    import io.circe.parser.parse
    parse(str).right.get
  }
}
