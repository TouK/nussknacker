package pl.touk.nussknacker.engine.sql

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneId}
import java.util
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

class SqlExpressionTest extends FunSuite with Matchers with PatientScalaFutures {

  private val validationContext = ValidationContext(Map[String, TypingResult](
    "var" -> Typed[String],
    "var1" -> TypedList(Map(
        "field1" -> Typed[String],
        "field2" -> Typed[java.lang.Long],
        "getField3" -> Typed[String],
        "localDateTimeField" -> Typed[LocalDateTime]
      )),
    "var12" -> TypedList(Map(
        "field3" -> Typed[String],
        "field4" -> Typed[java.lang.Long],
        "isField5" -> Typed[Boolean],
        "is_field6" -> Typed[Boolean]
    )),
    "var3" -> TypedList[TestBean]
  ))

  private val dateToTest = LocalDateTime.of(2018, 6, 6, 10, 11, 12)

  private val ctx = Context("").withVariables(Map(
    "var" -> "blah",
    "var1" -> util.Arrays.asList(TypedMap(ListMap(
      "field1" -> "abcd",
      "field2" -> 11L,
      "getField3" -> "tralaala",
      "localDateTimeField" -> dateToTest
    ))),
    "var12" -> util.Arrays.asList(TypedMap(ListMap(
      "field3" -> "eeeee",
      "field4" -> 13L,
      "isField5" -> false,
      "is_field6" -> true
    ))),
    "var3" -> util.Arrays.asList(TestBean("a", isField2 = true, 11L))
  ))

  test("evaluate case insensitive table names") {
    evaluate("select field1 from VAR1") shouldBe
      List(TypedMap(ListMap("FIELD1" -> "abcd")))
  }

  test("evaluate expression with getter-like column names") {
    evaluate("select isField5, is_field6 from var12") shouldBe
      List(TypedMap(ListMap("ISFIELD5" -> false, "IS_FIELD6" -> true)))

    val z = evaluate("select field1, isfield2, getField3 from var3 where isField2 = true")
    z shouldBe
      List(TypedMap(ListMap("FIELD1" -> "a", "ISFIELD2" -> true, "GETFIELD3" -> java.math.BigDecimal.valueOf(11))))
  }

  test("evaluate with var names containing each other") {
    evaluate("select field3 from VAR12 left outer join VAR1 on VAR1.field1 = VAR12.field3") shouldBe
      List(TypedMap(ListMap("FIELD3" -> "eeeee")))
  }

  test("use LocalDateTime field as timestamp") {
    evaluate("select localDateTimeField from VAR1") shouldBe
      List(TypedMap(ListMap("LOCALDATETIMEFIELD" -> Timestamp.from(dateToTest.atZone(ZoneId.systemDefault()).toInstant))))
  }

  private def evaluate(expression: String, ctx: Context = ctx, validationContext: ValidationContext = validationContext): List[TypedMap] =
    parseOrFail(expression, validationContext).evaluate[java.util.List[TypedMap]](ctx, Map.empty).asScala.toList

  private def parseOrFail(expression: String, validationContext: ValidationContext = validationContext): SqlExpression =
    SqlExpressionParser
          .parse(expression, validationContext, Typed.fromDetailedType[java.util.List[_]])
          .leftMap(err => fail(s"Failed to parse: $err")).merge.expression.asInstanceOf[SqlExpression]

  case class TestBean(field1: String, isField2: Boolean, getField3: Long)

}
