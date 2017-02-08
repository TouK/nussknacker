package pl.touk.esp.engine.spel

import java.math.BigDecimal
import java.text.ParseException
import java.time.LocalDate
import java.util.Collections

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.lazyy.{LazyContext, LazyValuesProvider, UsingLazyValues}
import pl.touk.esp.engine.compile.ValidationContext
import pl.touk.esp.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.esp.engine.types.EspTypeUtils

import scala.collection.JavaConverters._

class SpelExpressionSpec extends FlatSpec with Matchers {

  private val bigValue = BigDecimal.valueOf(4187338076L)

  val testValue = Test( "1", 2, List(Test("3", 4), Test("5", 6)).asJava, bigValue)
  val ctx = Context("abc",
    variables = Map("obj" -> testValue)
  )
  def dumbLazyProvider = new LazyValuesProvider {
    override def apply[T](ctx: LazyContext, serviceId: String, params: Seq[(String, Any)]) = throw new IllegalStateException("Shouln't be invoked")
  }

  private val enrichingServiceId = "serviceId"

  case class Test(id: String, value: Long, children: java.util.List[Test] = List[Test]().asJava, bigValue: BigDecimal = BigDecimal.valueOf(0L)) extends UsingLazyValues {
    val lazyVal = lazyValue[String](enrichingServiceId).map(_ + " ma kota")
  }

  private def parseOrFail(expr: String, context: Context = ctx, globalProcessVariables: Map[String, ClazzRef] = Map.empty) = {
    parse(expr, context, globalProcessVariables) match {
      case Valid(e) => e
      case Invalid(err) => throw new ParseException(err.message, -1)
    }
  }


  import pl.touk.esp.engine.util.Implicits._

  private def parse(expr: String, context: Context = ctx, globalProcessVariables: Map[String, ClazzRef] = Map.empty) = {
    val validationCtx = ValidationContext(
      context.variables.mapValuesNow(_.getClass).mapValuesNow(ClazzRef.apply),
      EspTypeUtils.clazzAndItsChildrenDefinition(context.variables.values.map(_.getClass).toList)
    )
    val expressionFunctions = Map("today" -> classOf[LocalDate].getDeclaredMethod("now"))
    new SpelExpressionParser(expressionFunctions, globalProcessVariables, getClass.getClassLoader).parse(expr, validationCtx)
  }

  it should "invoke simple expression" in {
    parseOrFail("#obj.value + 4").evaluate[Long](ctx, dumbLazyProvider).value should equal(6)
  }

  it should "invoke simple list expression" in {
    parseOrFail("{'1', '2'}.contains('2')").evaluate[Boolean](ctx, dumbLazyProvider).value shouldBe true
  }

  it should "handle big decimals" in {
    bigValue.compareTo(BigDecimal.valueOf(50*1024*1024)) should be > 0
    bigValue.compareTo(BigDecimal.valueOf(50*1024*1024L)) should be > 0
    parseOrFail("#obj.bigValue").evaluate[BigDecimal](ctx, dumbLazyProvider).value should equal(bigValue)
    parseOrFail("#obj.bigValue < 50*1024*1024").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(false)
    parseOrFail("#obj.bigValue < 50*1024*1024L").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(false)
  }

  it should "filter by list predicates" in {

    parseOrFail("#obj.children.?[id == '55'].empty").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(true)
    parseOrFail("#obj.children.?[id == '5'].size()").evaluate[Integer](ctx, dumbLazyProvider).value should equal(1: Integer)

  }

  it should "evaluate map " in {
    val ctxWithVar = ctx.withVariable("processVariables", Collections.singletonMap("processingStartTime", 11L))
    parseOrFail("#processVariables['processingStartTime']", ctxWithVar).evaluate[Long](ctxWithVar, dumbLazyProvider).value should equal(11L)
  }

  it should "perform date operations" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)

    parseOrFail("#date.until(T(java.time.LocalDate).now()).days", withDays).evaluate[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "register functions" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)

    parseOrFail("#date.until(#today()).days", withDays).evaluate[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "register global variables" in {
    val globalVars = Map("processHelper" -> ClazzRef.apply(SampleGlobalObject.getClass))
    parseOrFail("#processHelper.add(1, #processHelper.constant())", ctx, globalVars).evaluate[Integer](ctx, dumbLazyProvider).value should equal(5)
  }

  it should "allow access to maps in dot notation" in {
    val withMapVar = ctx.withVariable("map", Map("key1" -> "value1", "key2" -> 20).asJava)

    parseOrFail("#map.key1", withMapVar).evaluate[String](withMapVar, dumbLazyProvider).value should equal("value1")
    parseOrFail("#map.key2", withMapVar).evaluate[Integer](withMapVar, dumbLazyProvider).value should equal(20)

  }

  it should "evaluate using lazy value" in {
    val provided = "ala"
    val lazyValueProvider = new LazyValuesProvider {
      override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]) =
        (context.withEvaluatedValue(enrichingServiceId, params.toMap, provided), provided.asInstanceOf[T])
    }

    val valueWithModifiedContext = parseOrFail("#obj.lazyVal").evaluate[String](ctx, lazyValueProvider)
    valueWithModifiedContext.value shouldEqual "ala ma kota"
    valueWithModifiedContext.context.lazyContext[String](enrichingServiceId, Map.empty) shouldEqual provided
  }

  it should "not allow access to variables without hash in methods" in {
    val globalVars = Map("processHelper" -> ClazzRef.apply(SampleGlobalObject.getClass))
    val withNum = ctx.withVariable("a", 5)
    parse("#processHelper.add(a, 1)", withNum, globalVars) should matchPattern {
      case Invalid(ExpressionParseError("Non reference 'a' occurred. Maybe you missed '#' in front of it?")) =>
    }
  }

  it should "not allow unknown variables in methods" in {
    val globalVars = Map("processHelper" -> ClazzRef.apply(SampleGlobalObject.getClass))
    parse("#processHelper.add(#a, 1)", ctx, globalVars) should matchPattern {
      case Invalid(ExpressionParseError("Unresolved references a")) =>
    }
  }

  it should "validate expression with projection and filtering" in {
    val ctxWithInput = ctx.withVariable("input", SampleObject(List(SampleValue(444))))
    parse("(#input.list.?[value == 5]).![value].contains(5)", ctxWithInput, Map()) shouldBe 'valid
  }

  it should "validate map literals" in {
    val ctxWithInput = ctx.withVariable("input", SampleValue(444))
    parse("{ Field1: 'Field1Value', Field2: 'Field2Value', Field3: #input.value }", ctxWithInput, Map()) shouldBe 'valid
  }

  it should "evaluate static field/method using property syntax" in {
    val globalVars = Map("processHelper" -> ClazzRef.apply(SampleGlobalObject.getClass))
    parseOrFail("#processHelper.one", globalProcessVariables = globalVars).evaluate[Int](ctx, dumbLazyProvider).value should equal(1)
    parseOrFail("#processHelper.one()", globalProcessVariables = globalVars).evaluate[Int](ctx, dumbLazyProvider).value should equal(1)
    parseOrFail("#processHelper.constant", globalProcessVariables = globalVars).evaluate[Int](ctx, dumbLazyProvider).value should equal(4)
    parseOrFail("#processHelper.constant()", globalProcessVariables = globalVars).evaluate[Int](ctx, dumbLazyProvider).value should equal(4)
  }

}

case class SampleObject(list: List[SampleValue])

case class SampleValue(value: Int)


object SampleGlobalObject {
  val constant = 4
  def add(a: Int, b: Int): Int = a + b
  def one() = 1
}
