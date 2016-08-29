package pl.touk.esp.engine.graph

import java.text.ParseException
import java.time.LocalDate

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.Interpreter.ContextImpl
import pl.touk.esp.engine.api.{Context, LazyValuesProvider, MetaData, ValueWithModifiedContext}
import pl.touk.esp.engine.spel.SpelExpressionParser

import scala.collection.JavaConverters._

class ExpressionSpec extends FlatSpec with Matchers {

  val testValue = Test( "1", 2, List(Test("3", 4), Test("5", 6)).asJava)
  val ctx = ContextImpl(
    variables = Map("obj" -> testValue)
  )
  def dumbLazyProvider(ctx: Context) = new LazyValuesProvider {
    override def apply[T](serviceId: String, params: (String, Any)*) = throw new IllegalStateException("Shouln't be invoked")
  }

  private val enrichingServiceId = "serviceId"

  case class Test(id: String, value: Long, children: java.util.List[Test] = List[Test]().asJava) {
    def lazyValue(lazyValProvider: LazyValuesProvider): ValueWithModifiedContext[String] =
      lazyValProvider[String](enrichingServiceId).map(_ + " ma kota")
  }

  private def parse(expr: String) = {
    val expressionFunctions = Map("today" -> classOf[LocalDate].getDeclaredMethod("now"))
    new SpelExpressionParser(expressionFunctions).parse(expr) match {
      case Valid(e) => e
      case Invalid(err) => throw new ParseException(err.message, -1)
    }
  }

  it should "invoke simple expression" in {

    parse("#obj.value + 4").evaluate[Long](ctx, dumbLazyProvider).value should equal(6)

  }

  it should "filter by list predicates" in {

    parse("#obj.children.?[id == '55'].empty").evaluate[Boolean](ctx, dumbLazyProvider).value should equal(true)
    parse("#obj.children.?[id == '5'].size()").evaluate[Integer](ctx, dumbLazyProvider).value should equal(1: Integer)

  }

  it should "perform date operations" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)

    parse("#date.until(T(java.time.LocalDate).now()).days").evaluate[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "register functions" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)

    parse("#date.until(#today()).days").evaluate[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "allow access to maps in dot notation" in {
    val withMapVar = ctx.withVariable("map", Map("key1" -> "value1", "key2" -> 20).asJava)

    parse("#map.key1").evaluate[String](withMapVar, dumbLazyProvider).value should equal("value1")
    parse("#map.key2").evaluate[Integer](withMapVar, dumbLazyProvider).value should equal(20)

  }

  it should "evaluate using lazy value" in {
    val provided = "ala"
    def lazyValueProvider(c: Context) = new LazyValuesProvider {
      override def apply[T](serviceId: String, params: (String, Any)*) =
        ValueWithModifiedContext(
          provided,
          c.withLazyContext(c.lazyContext.withEvaluatedValue(enrichingServiceId, params.toMap, provided))
        ).asInstanceOf[ValueWithModifiedContext[T]]
    }

    val valueWithModifiedContext = parse("#obj.lazyValue").evaluate[String](ctx, lazyValueProvider)
    valueWithModifiedContext.value shouldEqual "ala ma kota"
    valueWithModifiedContext.context.lazyContext[String](enrichingServiceId, Map.empty) shouldEqual provided
  }


}
