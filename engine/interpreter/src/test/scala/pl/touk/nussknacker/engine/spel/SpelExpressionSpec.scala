package pl.touk.nussknacker.engine.spel

import java.math.BigDecimal
import java.text.ParseException
import java.time.LocalDate
import java.util
import java.util.Collections

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider, UsingLazyValues}
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap}
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParseError, TypedExpression, ValueWithLazyContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

class SpelExpressionSpec extends FlatSpec with Matchers {

  private class EvaluateSync(expression: Expression) {
    def evaluateSync[T](ctx: Context, lvp: LazyValuesProvider) : ValueWithLazyContext[T]
      = Await.result(expression.evaluate[T](ctx, lvp), 5 seconds)
  }

  private implicit val classLoader = getClass.getClassLoader

  private implicit def toEvaluateSync(expression: Expression) : EvaluateSync = new EvaluateSync(expression)

  private val bigValue = BigDecimal.valueOf(4187338076L)

  val testValue = Test( "1", 2, List(Test("3", 4), Test("5", 6)).asJava, bigValue)
  val ctx = Context("abc").withVariables(
    Map("obj" -> testValue,"strVal" -> "","mapValue" -> Map("foo" -> "bar").asJava)
  )
  val ctxWithGlobal : Context = ctx.withVariable("processHelper", SampleGlobalObject)

  def dumbLazyProvider = new LazyValuesProvider {
    override def apply[T](ctx: LazyContext, serviceId: String, params: Seq[(String, Any)]) = throw new IllegalStateException("Shouln't be invoked")
  }

  private val enrichingServiceId = "serviceId"

  case class Test(id: String, value: Long, children: java.util.List[Test] = List[Test]().asJava, bigValue: BigDecimal = BigDecimal.valueOf(0L)) extends UsingLazyValues {
    val lazyVal = lazyValue[String](enrichingServiceId).map(_ + " ma kota")
  }

  private def parseOrFail[T:ClassTag](expr: String, context: Context = ctx) : Expression = {
    parse(expr, context) match {
      case Valid(e) => e.expression
      case Invalid(err) => throw new ParseException(err.map(_.message).toList.mkString, -1)
    }
  }

  private def parseOrFail[T:ClassTag](expr: String, context: ValidationContext) : Expression = {
    parse(expr, context) match {
      case Valid(e) => e.expression
      case Invalid(err) => throw new ParseException(err.map(_.message).toList.mkString, -1)
    }
  }


  import pl.touk.nussknacker.engine.util.Implicits._

  private def parse[T:ClassTag](expr: String, context: Context = ctx) : ValidatedNel[ExpressionParseError, TypedExpression] = {
    val validationCtx = ValidationContext(
      context.variables.mapValuesNow(_.getClass).mapValuesNow(ClazzRef(_)).mapValuesNow(Typed.apply))
    parse(expr, validationCtx)
  }

  private def parse[T:ClassTag](expr: String, validationCtx: ValidationContext) : ValidatedNel[ExpressionParseError, TypedExpression] = {
    val expressionFunctions = Map("today" -> classOf[LocalDate].getDeclaredMethod("now"))
    val imports = List(SampleValue.getClass.getPackage.getName)
    new SpelExpressionParser(expressionFunctions, imports, getClass.getClassLoader, 1 minute, enableSpelForceCompile = true)
      .parse(expr, validationCtx, Typed[T])
  }

  it should "invoke simple expression" in {
    parseOrFail[java.lang.Number]("#obj.value + 4").evaluateSync[Long](ctx, dumbLazyProvider).value should equal(6)
  }

  it should "invoke simple list expression" in {
    parseOrFail[Boolean]("{'1', '2'}.contains('2')").evaluateSync[Boolean](ctx, dumbLazyProvider).value shouldBe true
  }

  it should "handle string concatenation correctly" in {
    parse[String]("'' + 1") shouldBe 'valid
    parse[Long]("2 + 1") shouldBe 'valid
    parse[String]("'' + ''") shouldBe 'valid
    parse[String]("4 + ''") shouldBe 'valid
  }


  it should "null properly" in {
    parse[String]("null") shouldBe 'valid
    parse[Long]("null") shouldBe 'valid
    parse[Any]("null") shouldBe 'valid
    parse[Boolean]("null") shouldBe 'valid
  }

  it should "invoke list variable reference with different concrete type after compilation" in {
    def contextWithList(value: Any) = ctx.withVariable("list", value)
    val expr = parseOrFail[Any]("#list", contextWithList(Collections.emptyList()))

    //first run - nothing happens, we bump the counter
    expr.evaluateSync[Any](contextWithList(null), dumbLazyProvider).value
    //second run - exitTypeDescriptor is set, expression is compiled
    expr.evaluateSync[Any](contextWithList(new util.ArrayList[String]()), dumbLazyProvider).value
    //third run - expression is compiled as ArrayList and we fail :(
    expr.evaluateSync[Any](contextWithList(Collections.emptyList()), dumbLazyProvider).value


  }

  // TODO: fixme
  ignore should "perform date operations" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)
    parseOrFail[Any]("#date.until(T(java.time.LocalDate).now())", withDays).evaluateSync[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  // TODO: fixme
  ignore should "register functions" in {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx.withVariable("date", twoDaysAgo)
    parseOrFail[Any]("#date.until(#today()).days", withDays).evaluateSync[Integer](withDays, dumbLazyProvider).value should equal(2)
  }

  it should "be possible to use SpEL's #this object" in {
    parseOrFail[Any]("{1, 2, 3}.?[ #this > 1]").evaluateSync[java.util.List[Integer]](ctx, dumbLazyProvider).value shouldBe util.Arrays.asList(2, 3)
    parseOrFail[Any]("{1, 2, 3}.![ #this > 1]").evaluateSync[java.util.List[Boolean]](ctx, dumbLazyProvider).value shouldBe util.Arrays.asList(false, true, true)
    parseOrFail[Any]("{'1', '22', '3'}.?[ #this.length > 1]").evaluateSync[java.util.List[Boolean]](ctx, dumbLazyProvider).value shouldBe util.Arrays.asList("22")
    parseOrFail[Any]("{'1', '22', '3'}.![ #this.length > 1]").evaluateSync[java.util.List[Boolean]](ctx, dumbLazyProvider).value shouldBe util.Arrays.asList(false, true, false)

  }

  it should "validate MethodReference" in {
    val parsed = parse[Any]("#processHelper.add(1, 1)", ctxWithGlobal)
    parsed.isValid shouldBe true
  }

  it should "return invalid type for MethodReference with invalid arity " in {
    val parsed = parse[Any]("#processHelper.add(1)", ctxWithGlobal)
    val expectedValidation = Invalid("Invalid arity for 'add'")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  it should "return invalid type for MethodReference with missing arguments" in {
    val parsed = parse[Any]("#processHelper.add()", ctxWithGlobal)
    val expectedValidation = Invalid("Invalid arity for 'add'")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  it should "type optimistically MethodReference" in {
    val parsed = parse[Any]("#processHelper.add(1, 1, 1)", ctxWithGlobal)
    parsed.isValid shouldBe true
  }

  it should "return invalid type if PropertyOrFieldReference does not exists" in {
    val parsed = parse[Any]("#processHelper.add", ctxWithGlobal)
    val expectedValidation =  Invalid("There is no property 'add' in type 'pl.touk.nussknacker.engine.spel.SampleGlobalObject$'")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  it should "handle big decimals" in {
    bigValue.compareTo(BigDecimal.valueOf(50*1024*1024)) should be > 0
    bigValue.compareTo(BigDecimal.valueOf(50*1024*1024L)) should be > 0
    parseOrFail[Any]("#obj.bigValue").evaluateSync[BigDecimal](ctx, dumbLazyProvider).value should equal(bigValue)
    parseOrFail[Boolean]("#obj.bigValue < 50*1024*1024").evaluateSync[Boolean](ctx, dumbLazyProvider).value should equal(false)
    parseOrFail[Boolean]("#obj.bigValue < 50*1024*1024L").evaluateSync[Boolean](ctx, dumbLazyProvider).value should equal(false)
  }

  it should "access list elements by index" in {
    parseOrFail[Any]("#obj.children[0].id").evaluateSync[String](ctx, dumbLazyProvider).value shouldEqual "3"
    parseOrFail[Any]("#mapValue['foo']").evaluateSync[String](ctx, dumbLazyProvider).value shouldEqual "bar"
  }

  it should "filter by list predicates" in {

    parseOrFail[Any]("#obj.children.?[id == '55'].isEmpty").evaluateSync[Boolean](ctx, dumbLazyProvider).value should equal(true)
    parseOrFail[Any]("#obj.children.?[id == '55' || id == '66'].isEmpty").evaluateSync[Boolean](ctx, dumbLazyProvider).value should equal(true)
    parseOrFail[Any]("#obj.children.?[id == '5'].size()").evaluateSync[Integer](ctx, dumbLazyProvider).value should equal(1: Integer)
    parseOrFail[Any]("#obj.children.?[id == '5' || id == '3'].size()").evaluateSync[Integer](ctx, dumbLazyProvider).value should equal(2: Integer)
    parseOrFail[Any]("#obj.children.?[id == '5' || id == '3'].![value]")
      .evaluateSync[util.ArrayList[Long]](ctx, dumbLazyProvider).value should equal(new util.ArrayList(util.Arrays.asList(4L, 6L)))
    parseOrFail[Any]("(#obj.children.?[id == '5' || id == '3'].![value]).contains(4L)")
      .evaluateSync[Boolean](ctx, dumbLazyProvider).value should equal(true)

  }

  it should "evaluate map " in {
    val ctxWithVar = ctx.withVariable("processVariables", Collections.singletonMap("processingStartTime", 11L))
    parseOrFail[Any]("#processVariables['processingStartTime']", ctxWithVar).evaluateSync[Long](ctxWithVar, dumbLazyProvider).value should equal(11L)
  }

  it should "stop validation when property of Any/Object type found" in {
    val ctxWithVar = ctx.withVariable("obj", SampleValue(11, ""))
    parse("#obj.anyObject.anyPropertyShouldValidate", ctxWithVar) shouldBe 'valid

  }

  it should "return sane error with empty expression " in {
    parse("", ctx) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("No node"), Nil)) =>
    }
  }

  it should "register static variables" in {
    parseOrFail[Any]("#processHelper.add(1, #processHelper.constant())", ctxWithGlobal).evaluateSync[Integer](ctxWithGlobal, dumbLazyProvider).value should equal(5)
  }

  it should "allow access to maps in dot notation" in {
    val withMapVar = ctx.withVariable("map", Map("key1" -> "value1", "key2" -> 20).asJava)

    parseOrFail[String]("#map.key1", withMapVar).evaluateSync[String](withMapVar, dumbLazyProvider).value should equal("value1")
    parseOrFail[Integer]("#map.key2", withMapVar).evaluateSync[Integer](withMapVar, dumbLazyProvider).value should equal(20)

  }

  it should "allow access to objects with get method in dot notation" in {
    val withObjVar = ctx.withVariable("obj", new SampleObjectWithGetMethod(Map("key1" -> "value1", "key2" -> 20)))

    parseOrFail[String]("#obj.key1", withObjVar).evaluateSync[String](withObjVar, dumbLazyProvider).value should equal("value1")
    parseOrFail[Integer]("#obj.key2", withObjVar).evaluateSync[Integer](withObjVar, dumbLazyProvider).value should equal(20)
  }

  it should "allow access to statics" in {
    val withMapVar = ctx.withVariable("longClass", classOf[java.lang.Long])
    parseOrFail[Any]("#longClass.valueOf('44')", withMapVar)
      .evaluateSync[Long](withMapVar, dumbLazyProvider).value should equal(44l)

    parseOrFail[Any]("T(java.lang.Long).valueOf('44')", ctx)
      .evaluateSync[Long](ctx, dumbLazyProvider).value should equal(44l)
  }

  it should "should != correctly for compiled expression - expression is compiled when invoked for the 3rd time" in {
    //see https://jira.spring.io/browse/SPR-9194 for details
    val empty = new String("")
    val withMapVar = ctx.withVariable("emptyStr", empty)

    val expression = parseOrFail[Boolean]("#emptyStr != ''", withMapVar)
    expression.evaluateSync[Boolean](withMapVar, dumbLazyProvider).value should equal(false)
    expression.evaluateSync[Boolean](withMapVar, dumbLazyProvider).value should equal(false)
    expression.evaluateSync[Boolean](withMapVar, dumbLazyProvider).value should equal(false)
  }

  it should "evaluate using lazy value" in {
    val provided = "ala"
    val lazyValueProvider = new LazyValuesProvider {
      override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]) =
        IO.pure((context.withEvaluatedValue(enrichingServiceId, params.toMap, Left(provided)), provided.asInstanceOf[T]))
    }

    val valueWithModifiedContext = parseOrFail[Any]("#obj.lazyVal").evaluateSync[String](ctx, lazyValueProvider)
    valueWithModifiedContext.value shouldEqual "ala ma kota"
    valueWithModifiedContext.lazyContext[String](enrichingServiceId, Map.empty) shouldEqual provided
  }

  it should "not allow access to variables without hash in methods" in {
    val withNum = ctx.withVariable("a", 5).withVariable("processHelper", SampleGlobalObject)
    parse("#processHelper.add(a, 1)", withNum) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'a' occurred. Maybe you missed '#' in front of it?"), Nil)) =>
    }
  }

  it should "not allow unknown variables in methods" in {
    parse("#processHelper.add(#a, 1)", ctx.withVariable("processHelper", SampleGlobalObject.getClass)) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference a"), Nil)) =>
    }

    parse("T(pl.touk.nussknacker.engine.spel.SampleGlobalObject).add(#a, 1)", ctx) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference a"), Nil)) =>
    }
  }

  it should "not allow vars without hashes in equality condition" in {
    parse("nonexisting == 'ala'", ctx) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'nonexisting' occurred. Maybe you missed '#' in front of it?"), Nil)) =>
    }
  }

  it should "validate ternary operator" in {
    parse[Long]("'d'? 3 : 4", ctx) should not be 'valid
    parse[String]("1 > 2 ? 12 : 23", ctx) should not be 'valid
    parse[Long]("1 > 2 ? 12 : 23", ctx) shouldBe 'valid
    parse[String]("1 > 2 ? 'ss' : 'dd'", ctx) shouldBe 'valid
  }

  it should "validate selection for inline list" in {
    parse[Long]("{44, 44}.?[#this.alamakota]", ctx) should not be 'valid
    parse[java.util.List[_]]("{44, 44}.?[#this > 4]", ctx) shouldBe 'valid


  }

  it should "validate selection and projection for list variable" in {
    implicit val id = NodeId("")
    val vctx = ValidationContext.empty.withVariable("a", Typed(ClazzRef(classOf[java.util.List[_]], List(ClazzRef[String])))).toOption.get

    parse[java.util.List[_]]("#a.![#this.length()].?[#this > 4]", vctx) shouldBe 'valid
    parse[java.util.List[_]]("#a.![#this / 5]", vctx) should not be 'valid
  }

  it should "allow #this reference inside functions" in {
    parseOrFail[java.util.List[String]]("{1, 2, 3}.!['ala'.substring(#this - 1)]", ctx)
      .evaluateSync[java.util.List[String]](ctx, dumbLazyProvider).value.asScala.toList shouldBe List("ala", "la", "a")
  }

  it should "allow property access in unknown classes" in {
    parse[Any]("#input.anyObject", ValidationContext(Map("input" -> Typed[SampleValue]))) shouldBe 'valid
  }

  it should "validate expression with projection and filtering" in {
    val ctxWithInput = ctx.withVariable("input", SampleObject(List(SampleValue(444))))
    parse[Any]("(#input.list.?[value == 5]).![value].contains(5)", ctxWithInput) shouldBe 'valid
  }

  it should "validate map literals" in {
    val ctxWithInput = ctx.withVariable("input", SampleValue(444))
    parse[Any]("{ Field1: 'Field1Value', Field2: 'Field2Value', Field3: #input.value }", ctxWithInput) shouldBe 'valid
  }

  it should "type map literals" in {
    val ctxWithInput = ctx.withVariable("input", SampleValue(444))
    parse[Any]("{ Field1: 'Field1Value', Field2: #input.value }.Field1", ctxWithInput) shouldBe 'valid
    parse[Any]("{ Field1: 'Field1Value', 'Field2': #input }.Field2.value", ctxWithInput) shouldBe 'valid
    parse[Any]("{ Field1: 'Field1Value', Field2: #input }.noField", ctxWithInput) shouldNot be ('valid)

  }


  it should "validate lazy value usage" in {
    val ctxWithInput = ctx.withVariable("input", SampleValue(444))
    parse[String]("#input.lazy1", ctxWithInput) shouldBe 'valid
    parse[Long]("#input.lazy2", ctxWithInput) shouldBe 'valid

  }

  it should "not validate plain string " in {
    parse("abcd", ctx) shouldNot be ('valid)
  }

  it should "evaluate static field/method using property syntax" in {
    parseOrFail[Any]("#processHelper.one", ctxWithGlobal).evaluateSync[Int](ctxWithGlobal, dumbLazyProvider).value should equal(1)
    parseOrFail[Any]("#processHelper.one()", ctxWithGlobal).evaluateSync[Int](ctxWithGlobal, dumbLazyProvider).value should equal(1)
    parseOrFail[Any]("#processHelper.constant", ctxWithGlobal).evaluateSync[Int](ctxWithGlobal, dumbLazyProvider).value should equal(4)
    parseOrFail[Any]("#processHelper.constant()", ctxWithGlobal).evaluateSync[Int](ctxWithGlobal, dumbLazyProvider).value should equal(4)
  }

  it should "detect bad type of literal or variable" in {

    def shouldHaveBadType(valid: Validated[NonEmptyList[ExpressionParseError], _], message: String) = valid should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError(msg), _)) if msg == message =>
    }

    shouldHaveBadType( parse[Int]("'abcd'", ctx), "Bad expression type, expected: type 'int', found: type 'java.lang.String'" )
    shouldHaveBadType( parse[String]("111", ctx), "Bad expression type, expected: type 'java.lang.String', found: type 'java.lang.Integer'" )
    shouldHaveBadType( parse[String]("{1, 2, 3}", ctx), "Bad expression type, expected: type 'java.lang.String', found: type 'java.util.List'" )
    shouldHaveBadType( parse[java.util.Map[_, _]]("'alaMa'", ctx), "Bad expression type, expected: type 'java.util.Map', found: type 'java.lang.String'" )
    shouldHaveBadType( parse[Int]("#strVal", ctx), "Bad expression type, expected: type 'int', found: type 'java.lang.String'" )

  }

  it should "resolve imported package" in {
    val givenValue = 123
    parseOrFail[Int](s"new SampleValue($givenValue, '').value").evaluateSync(ctx, dumbLazyProvider).value should equal(givenValue)
  }

  it should "parse typed map with existing field" in {

    implicit val nid = NodeId("")
    val ctxWithMap = ValidationContext
      .empty
      .withVariable("input", TypedObjectTypingResult(Map("str" -> Typed[String], "lon" -> Typed[Long]))).toOption.get


    parse[String]("#input.str", ctxWithMap) should be ('valid)
    parse[Long]("#input.lon", ctxWithMap) should be ('valid)

    parse[Long]("#input.str", ctxWithMap) shouldNot be ('valid)
    parse[String]("#input.ala", ctxWithMap) shouldNot be ('valid)
  }

  it should "evaluate parsed map" in {
    implicit val nid = NodeId("")
    val valCtxWithMap = ValidationContext
      .empty
      .withVariable("input", TypedObjectTypingResult(Map("str" -> Typed[String], "lon" -> Typed[Long]))).toOption.get

    val ctx = Context("").withVariable("input", TypedMap(Map("str" -> "aaa", "lon" -> 3444)))

    parseOrFail[String]("#input.str", valCtxWithMap).evaluateSync(ctx, dumbLazyProvider).value shouldBe "aaa"
    parseOrFail[Long]("#input.lon", valCtxWithMap).evaluateSync(ctx, dumbLazyProvider).value shouldBe 3444

  }

  it should "be able to type toString()" in {
    parse[Any]("12.toString()", ctx).toOption.get.returnType shouldBe Typed[String]
  }
  
  it should "be able to type string concatenation" in {
    parse[Any]("12 + ''", ctx).toOption.get.returnType shouldBe Typed[String]
    parse[Any]("'' + 12", ctx).toOption.get.returnType shouldBe Typed[String]
  }

}

case class SampleObject(list: List[SampleValue])

case class SampleValue(value: Int, anyObject: Any = "") extends UsingLazyValues {

  val lazy1 : LazyState[String] = lazyValue[String]("")

  val lazy2 : LazyState[Long] = lazyValue[Long]("")

}

object SampleGlobalObject {
  val constant = 4
  def add(a: Int, b: Int): Int = a + b
  def one() = 1
  def identityMap(map: java.util.Map[String, Any]): java.util.Map[String, Any] = map
}

class SampleObjectWithGetMethod(map: Map[String, Any]) {

  def get(field: String): Any = map.getOrElse(field, throw new IllegalArgumentException(s"No such field: $field"))

}