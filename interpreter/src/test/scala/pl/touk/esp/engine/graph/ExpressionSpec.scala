package pl.touk.esp.engine.graph

import java.math.BigDecimal
import java.text.ParseException
import java.time.LocalDate

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.lazyy.{LazyContext, LazyValuesProvider, UsingLazyValues}
import pl.touk.esp.engine.compile.ValidationContext
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.esp.engine.spel.SpelExpressionParser
import pl.touk.esp.engine.types.EspTypeUtils

import scala.collection.JavaConverters._

class ExpressionSpec extends FlatSpec with Matchers {

  private val bigValue = BigDecimal.valueOf(4187338076L)
  
  val testValue = Test( "1", 2, List(Test("3", 4), Test("5", 6)).asJava, bigValue)
  val ctx = Context(
    variables = Map("obj" -> testValue)
  )
  def dumbLazyProvider = new LazyValuesProvider {
    override def apply[T](ctx: LazyContext, serviceId: String, params: Seq[(String, Any)]) = throw new IllegalStateException("Shouln't be invoked")
  }

  private val enrichingServiceId = "serviceId"

  case class Test(id: String, value: Long, children: java.util.List[Test] = List[Test]().asJava, bigValue: BigDecimal = BigDecimal.valueOf(0L)) extends UsingLazyValues {
    val lazyVal = lazyValue[String](enrichingServiceId).map(_ + " ma kota")
  }

  private def parse(expr: String, context: Context = ctx) = {
    val validationCtx = ValidationContext(
      context.variables.mapValues(_.getClass).mapValues(ClazzRef.apply),
      EspTypeUtils.clazzAndItsChildrenDefinition(context.variables.values.map(_.getClass).toList)
    )
    val expressionFunctions = Map("today" -> classOf[LocalDate].getDeclaredMethod("now"))
    new SpelExpressionParser(expressionFunctions).parse(expr, validationCtx) match {
      case Valid(e) => e
      case Invalid(err) => throw new ParseException(err.message, -1)
    }
  }

  it should "invoke simple expression" in {
    parse("#obj.value + 4").evaluate[Long](ctx, dumbLazyProvider).value should equal(6)
  }

  it should "invoke simple list expression" in {
    parse("{'1', '2'}.contains('2')").evaluate[Boolean](ctx, dumbLazyProvider).value shouldBe true
  }

  it should "handle big decimals" in {
    bigValue.compareTo(BigDecimal.valueOf(50*1024*1024)) should be > 0
    bigValue.compareTo(BigDecimal.valueOf(50*1024*1024L)) should be > 0
    parse("#obj.bigValue").evaluate[BigDecimal](ctx, dumbLazyProvider).value should equal(bigValue)
    parse("#obj.bigValue < 50*1024*1024").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(false)
    parse("#obj.bigValue < 50*1024*1024L").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(false)
  }

  it should "filter by list predicates" in {

    parse("#obj.children.?[id == '55'].empty").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(true)
    parse("#obj.children.?[id == '5'].size()").evaluate[Integer](ctx, dumbLazyProvider).value should equal(1: Integer)

  }

  it should "perform date operations" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)

    parse("#date.until(T(java.time.LocalDate).now()).days", withDays).evaluate[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "register functions" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)

    parse("#date.until(#today()).days", withDays).evaluate[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "allow access to maps in dot notation" in {
    val withMapVar = ctx.withVariable("map", Map("key1" -> "value1", "key2" -> 20).asJava)

    parse("#map.key1", withMapVar).evaluate[String](withMapVar, dumbLazyProvider).value should equal("value1")
    parse("#map.key2", withMapVar).evaluate[Integer](withMapVar, dumbLazyProvider).value should equal(20)

  }

  it should "evaluate using lazy value" in {
    val provided = "ala"
    val lazyValueProvider = new LazyValuesProvider {
      override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]) =
        (context.withEvaluatedValue(enrichingServiceId, params.toMap, provided), provided.asInstanceOf[T])
    }

    val valueWithModifiedContext = parse("#obj.lazyVal").evaluate[String](ctx, lazyValueProvider)
    valueWithModifiedContext.value shouldEqual "ala ma kota"
    valueWithModifiedContext.context.lazyContext[String](enrichingServiceId, Map.empty) shouldEqual provided
  }


}
