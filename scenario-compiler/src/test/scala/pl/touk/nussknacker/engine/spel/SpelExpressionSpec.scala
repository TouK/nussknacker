package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.catsSyntaxValidatedId
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalacheck.Gen
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.springframework.util.{NumberUtils, StringUtils}
import pl.touk.nussknacker.engine.api.{Context, Hidden, NodeId, SpelExpressionExcludeList, TemplateEvaluationResult}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictInstance}
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.generics.{
  ExpressionParseError,
  GenericFunctionTypingError,
  GenericType,
  TypingFunction
}
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, _}
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, ClassDefinitionTestUtils, JavaClassWithVarargs}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.{
  ArgumentTypeError,
  ExpressionTypeError,
  GenericFunctionError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.{
  IllegalInvocationError,
  IllegalProjectionSelectionError,
  InvalidMethodReference,
  TypeReferenceError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.{
  NoPropertyError,
  UnknownClassError,
  UnknownMethodError,
  UnresolvedReferenceError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.OperatorError._
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.UnsupportedOperationError.ArrayConstructorError
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.{Flavour, Standard}
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.springframework.util.BigDecimalScaleEnsurer
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.lang.{
  Boolean => JBoolean,
  Byte => JByte,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong,
  Short => JShort
}
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.nio.charset.{Charset, StandardCharsets}
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZoneOffset}
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.util
import java.util.{Collections, Currency, List => JList, Locale, Map => JMap, Optional, UUID}
import java.util.concurrent.Executors
import scala.annotation.varargs
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}

class SpelExpressionSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage with OptionValues {

  private implicit class ValidatedExpressionOps[E](validated: Validated[E, TypedExpression]) {
    def validExpression: TypedExpression = validated.validValue
  }

  private implicit class EvaluateSyncTyped(expression: TypedExpression) {

    def evaluateSync[T](ctx: Context = ctx, skipReturnTypeCheck: Boolean = false): T = {
      val evaluationResult = expression.expression.evaluate[T](ctx, Map.empty)
      expression.typingInfo.typingResult match {
        case result: SingleTypingResult if evaluationResult != null && !skipReturnTypeCheck =>
          result.runtimeObjType.klass isAssignableFrom evaluationResult.getClass shouldBe true
        case _ =>
      }
      evaluationResult
    }

  }

  private implicit class EvaluateSyncCompiled(expression: CompiledExpression) {
    def evaluateSync[T](ctx: Context = ctx): T = expression.evaluate(ctx, Map.empty)
  }

  private implicit val nid: NodeId = NodeId("")

  private val bigValue = JBigDecimal.valueOf(4187338076L)

  private val testValue = Test("1", 2, List(Test("3", 4), Test("5", 6)).asJava, bigValue)

  private val ctx = Context("abc").withVariables(
    Map(
      "obj"                                         -> testValue,
      "strVal"                                      -> "",
      "mapValue"                                    -> Map("foo" -> "bar").asJava,
      "array"                                       -> Array("a", "b"),
      "intArray"                                    -> Array(1, 2, 3),
      "nestedArray"                                 -> Array(Array(1, 2), Array(3, 4)),
      "arrayOfUnknown"                              -> Array("unknown".asInstanceOf[Any]),
      "unknownString"                               -> ContainerOfUnknown("unknown"),
      "setVal"                                      -> Set("a").asJava,
      "containerWithUnknownObject"                  -> ContainerOfUnknown(SampleValue(1)),
      "containerWithUnknownObjectWithStaticMethods" -> ContainerOfUnknown(new JavaClassWithStaticParameterlessMethod()),
      "containerWithUnknownClassWithStaticMethods" -> ContainerOfUnknown(
        classOf[JavaClassWithStaticParameterlessMethod]
      ),
      "containerWithUnknownArray" -> ContainerOfUnknown(Array("a", "b", "c")),
    )
  )

  private val ctxWithGlobal: Context = ctx
    .withVariable("processHelper", SampleGlobalObject)
    .withVariable("javaClassWithVarargs", new JavaClassWithVarargs)

  val maxSpelExpressionLimit = 10000

  private def bigExpression(stringVar: String, length: Int): String = {
    val builder = new StringBuilder()
    builder
      .append("'")
      .append(" ".padTo(length, ' '))
      .append("'+ ")
      .append(stringVar)
      .result()
  }

  case class Test(
      id: String,
      value: Long,
      children: java.util.List[Test] = List[Test]().asJava,
      bigValue: JBigDecimal = JBigDecimal.valueOf(0L)
  )

  case class ContainerOfUnknown(value: Any)

  case class ContainerOfGenericMap(value: JMap[_, _])

  import pl.touk.nussknacker.engine.util.Implicits._

  private def parse[T: TypeTag](
      expr: String,
      context: Context = ctx,
      dictionaries: Map[String, DictDefinition] = Map.empty,
      flavour: Flavour = Standard,
      strictMethodsChecking: Boolean = defaultStrictMethodsChecking,
      staticMethodInvocationsChecking: Boolean = defaultStaticMethodInvocationsChecking,
      methodExecutionForUnknownAllowed: Boolean = defaultMethodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed: Boolean = defaultDynamicPropertyAccessAllowed
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    val validationCtx = ValidationContext(context.variables.mapValuesNow(Typed.fromInstance))
    parseV(
      expr,
      validationCtx,
      dictionaries,
      flavour,
      strictMethodsChecking = strictMethodsChecking,
      staticMethodInvocationsChecking = staticMethodInvocationsChecking,
      methodExecutionForUnknownAllowed = methodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed = dynamicPropertyAccessAllowed
    )
  }

  private def parseV[T: TypeTag](
      expr: String,
      validationCtx: ValidationContext,
      dictionaries: Map[String, DictDefinition] = Map.empty,
      flavour: Flavour = Standard,
      strictMethodsChecking: Boolean = defaultStrictMethodsChecking,
      staticMethodInvocationsChecking: Boolean = defaultStaticMethodInvocationsChecking,
      methodExecutionForUnknownAllowed: Boolean = defaultMethodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed: Boolean = defaultDynamicPropertyAccessAllowed
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    expressionParser(
      validationCtx.variables.values.toSeq,
      dictionaries,
      flavour,
      strictMethodsChecking,
      staticMethodInvocationsChecking,
      methodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed
    )
      .parse(expr, validationCtx, Typed.fromDetailedType[T])
  }

  private def expressionParser(
      globalVariableTypes: Seq[TypingResult] = Seq.empty,
      dictionaries: Map[String, DictDefinition] = Map.empty,
      flavour: Flavour = Standard,
      strictMethodsChecking: Boolean = defaultStrictMethodsChecking,
      staticMethodInvocationsChecking: Boolean = defaultStaticMethodInvocationsChecking,
      methodExecutionForUnknownAllowed: Boolean = defaultMethodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed: Boolean = defaultDynamicPropertyAccessAllowed
  ) = {
    val imports = List(SampleValue.getClass.getPackage.getName)
    val expressionConfig = ModelDefinitionBuilder.emptyExpressionConfig.copy(
      globalImports = imports,
      strictMethodsChecking = strictMethodsChecking,
      staticMethodInvocationsChecking = staticMethodInvocationsChecking,
      methodExecutionForUnknownAllowed = methodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed = dynamicPropertyAccessAllowed,
      spelExpressionExcludeList = spelExpressionExcludeListWithCustomPatterns
    )
    SpelExpressionParser.default(
      getClass.getClassLoader,
      expressionConfig,
      new SimpleDictRegistry(dictionaries),
      enableSpelForceCompile = true,
      flavour,
      classDefinitionSetWithCustomClasses(globalVariableTypes)
    )
  }

  private def spelExpressionExcludeListWithCustomPatterns: SpelExpressionExcludeList = {
    SpelExpressionExcludeList(
      List(
        "java\\.lang\\.System".r,
        "java\\.lang\\.reflect".r,
        "java\\.lang\\.net".r,
        "java\\.lang\\.io".r,
        "java\\.lang\\.nio".r
      )
    )
  }

  private def classDefinitionSetWithCustomClasses(globalVariableTypes: Seq[TypingResult]): ClassDefinitionSet = {
    val typesFromGlobalVariables = globalVariableTypes
      .flatMap(_.asInstanceOf[KnownTypingResult] match {
        case single: SingleTypingResult => Seq(single)
        case union: TypedUnion          => union.possibleTypes.toList
      })
      .map(_.runtimeObjType.klass)
    val customClasses = Seq(
      classOf[String],
      classOf[java.text.NumberFormat],
      classOf[java.lang.Long],
      classOf[java.lang.Integer],
      classOf[java.math.BigInteger],
      classOf[java.math.MathContext],
      classOf[java.math.BigDecimal],
      classOf[LocalDate],
      classOf[ChronoLocalDate],
      classOf[Locale],
      classOf[Charset],
      classOf[Currency],
      classOf[SampleValue],
      classOf[JavaClassWithStaticParameterlessMethod],
      Class.forName("pl.touk.nussknacker.engine.spel.SampleGlobalObject")
    )
    ClassDefinitionTestUtils.createDefinitionForClassesWithExtensions(typesFromGlobalVariables ++ customClasses: _*)
  }

  private def evaluate[T: TypeTag](expr: String, context: Context = ctx): T =
    parse[T](expr = expr, context = context).validExpression.evaluateSync[T](context)

  test("should be able to dynamically index record") {
    evaluate[Int]("{a: 5, b: 10}[#input.toString()]", Context("abc").withVariable("input", "a")) shouldBe 5
    evaluate[Integer]("{a: 5, b: 10}[#input.toString()]", Context("abc").withVariable("input", "asdf")) shouldBe null
  }

  test("should figure out result type when dynamically indexing record") {
    evaluate[Int](
      "{a: {g: 5, h: 10}, b: {g: 50, h: 100}}[#input.toString()].h",
      Context("abc").withVariable("input", "b")
    ) shouldBe 100
  }

  test("parsing first selection on array") {
    parse[Any]("{1,2,3,4,5,6,7,8,9,10}.^[(#this%2==0)]").validExpression
      .evaluateSync[java.util.ArrayList[Int]](ctx) should equal(2)
  }

  test("parsing expression that exceeding the limit") {
    parse[String](bigExpression("#strVal", maxSpelExpressionLimit)).validExpression
      .evaluateSync[String](ctx) should equal(" ".padTo(maxSpelExpressionLimit, ' '))
  }

  test("parsing last selection on array") {
    parse[Any]("{1,2,3,4,5,6,7,8,9,10}.$[(#this%2==0)]").validExpression
      .evaluateSync[java.util.ArrayList[Int]](ctx) should equal(10)
  }

  test("parsing Indexer on array") {
    parse[Any]("{1,2,3,4,5,6,7,8,9,10}[0]").validExpression.evaluateSync[Any](ctx) should equal(1)
  }

  test(
    "should not allow to call parameterless method in indexer on unknown that is not allowed in class definitions (IndexedType.OBJECT case)"
  ) {
    // we test here on non-(map, array, collection, string) object because those types handling is hardcoded in Indexer.getValueRef method and is quite safe
    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any](
        "#containerWithUnknownObject.value['productArity']", // productArity is not exposed method in ClassDefinitionSet
      ).validExpression.evaluateSync[Any](ctx)
    }
  }

  test(
    "should allow to invoke not allowed parentless method in indexer on unknown when dynamicPropertyAccessAllowed=true"
  ) {
    parse[Any](
      "#containerWithUnknownObject.value['productArity']",
      dynamicPropertyAccessAllowed = true
    ).validExpression.evaluateSync[Any](ctx) shouldBe 2
  }

  test("should allow to call parameterless method in indexer on unknown that is allowed in class definitions") {
    parse[AnyRef]("#containerWithUnknownObject.value['toString']").validExpression
      .evaluateSync[AnyRef](ctx) shouldBe "SampleValue(1,)"
  }

  test(
    "should allow to call parameterless method in indexer on unknown that is allowed in class definitions - reflective property accessor"
  ) {
    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any]("#containerWithUnknownObject.value['class']").validExpression
        .evaluateSync[AnyRef](ctx)
    }
  }

  test(
    "should not allow to call parameterless method in indexer on unknown that is not allowed in class definitions - reflective property accessor"
  ) {
    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any]("#containerWithUnknownObject.value['getSomeHiddenGetter']").validExpression
        .evaluateSync[AnyRef](ctx)
    }
  }

  test("indexer access on unknown - static methods on objects case") {
    parse[Any](
      "#containerWithUnknownObjectWithStaticMethods.value['someAllowedParameterlessStaticMethod']"
    ).validExpression
      .evaluateSync[AnyRef](ctx) shouldBe "allowed"

    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any](
        "#containerWithUnknownObjectWithStaticMethods.value['someHiddenParameterlessStaticMethod']"
      ).validExpression
        .evaluateSync[AnyRef](ctx)
    }
  }

  test("indexer access on unknown - static methods on class types case") {
    parse[Any](
      "#containerWithUnknownClassWithStaticMethods.value['someAllowedParameterlessStaticMethod']"
    ).validExpression
      .evaluateSync[AnyRef](ctx) shouldBe "allowed"

    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any](
        "#containerWithUnknownClassWithStaticMethods.value['someHiddenParameterlessStaticMethod']"
      ).validExpression
        .evaluateSync[AnyRef](ctx)
    }
  }

  test("should set large enough scale when converting to big decimal so that division by 2 works as expected") {
    val result = evaluate[Any]("""(1).toBigDecimal / 2""".stripMargin)
    BigDecimal(result.asInstanceOf[java.math.BigDecimal]) shouldBe BigDecimal(0.5) +- BigDecimal(0.001)
  }

  test("should set scale at least default when creating big decimal from int") {
    val result = evaluate[Any]("""(1).toBigDecimal""".stripMargin)
    result.asInstanceOf[java.math.BigDecimal].scale() shouldBe BigDecimalScaleEnsurer.DEFAULT_BIG_DECIMAL_SCALE
  }

  test("should set scale at least default when creating big decimal from big int") {
    val result = evaluate[Any]("""#a.toBigDecimal""".stripMargin, Context("asd", Map("a" -> JBigInteger.ONE)))
    result.asInstanceOf[java.math.BigDecimal].scale() shouldBe BigDecimalScaleEnsurer.DEFAULT_BIG_DECIMAL_SCALE
  }

  test("should set scale at least default when creating big decimal from string with low scale") {
    val result = evaluate[Any]("""("1.23").toBigDecimal""".stripMargin)
    result.asInstanceOf[java.math.BigDecimal].scale() shouldBe BigDecimalScaleEnsurer.DEFAULT_BIG_DECIMAL_SCALE
  }

  test("should set high scale when creating big decimal from string with high scale") {
    val result = evaluate[Any]("""("1.345345345345345345345345345345").toBigDecimal""".stripMargin)
    result.asInstanceOf[java.math.BigDecimal].scale() shouldBe 30
  }

  test("indexer access on unknown - array like case") {
    parse[Any](
      "#containerWithUnknownArray.value[0]"
    ).validExpression
      .evaluateSync[AnyRef](ctx) shouldBe "a"
  }

  test("projection access on unknown - array like case") {
    parse[java.util.List[Object]](
      "#containerWithUnknownArray.value.![#this.toString() + \"Val\"]"
    ).validExpression
      .evaluateSync[java.util.ArrayList[String]](ctx) should equal(util.Arrays.asList("aVal", "bVal", "cVal"))
  }

  test("selection access on unknown - array like case") {
    parse[java.util.List[Object]](
      "#containerWithUnknownArray.value.?[#this.toString() == \"b\"]"
    ).validExpression
      .evaluateSync[java.util.ArrayList[String]](ctx) should equal(util.Arrays.asList("b"))
  }

  test("parsing Selection on array") {
    parse[Any]("{1,2,3,4,5,6,7,8,9,10}.?[(#this%2==0)]").validExpression
      .evaluateSync[java.util.ArrayList[Int]](ctx) should equal(util.Arrays.asList(2, 4, 6, 8, 10))
  }

  test("parsing Projection on array") {
    parse[Any]("{1,2,3,4,5,6,7,8,9,10}.![(#this%2==0)]").validExpression.evaluateSync[java.util.ArrayList[Boolean]](
      ctx
    ) should equal(util.Arrays.asList(false, true, false, true, false, true, false, true, false, true))
  }

  test("parsing method with return type of array") {
    parse[Any]("'t,e,s,t'.split(',')").validExpression.evaluateSync[Any](ctx) should equal(Array("t", "e", "s", "t"))
  }

  test("parsing method with return type of array, selection on result") {
    parse[Any]("'t,e,s,t'.split(',').?[(#this=='t')]").validExpression.evaluateSync[Any](ctx) should equal(
      Array("t", "t")
    )
    parse[Any]("'t,e,s,t'.split(',')[2]").validExpression.evaluateSync[Any](ctx) shouldEqual "s"
  }

  test("blocking excluded reflect in runtime, without previous static validation") {
    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any](
        "T(java.lang.reflect.Modifier).classModifiers()",
        staticMethodInvocationsChecking = false,
        methodExecutionForUnknownAllowed = true
      ).validExpression.evaluateSync[Any](ctx)
    }
  }

  test("blocking excluded System in runtime, without previous static validation") {
    a[SpelExpressionEvaluationException] should be thrownBy {
      parse[Any](
        "T(System).exit()",
        staticMethodInvocationsChecking = false,
        methodExecutionForUnknownAllowed = true
      ).validExpression.evaluateSync[Any](ctx)
    }
  }

  test("blocking excluded in runtime, without previous static validation, allowed class and package") {
    parse[JBigInteger](
      "T(java.math.BigInteger).valueOf(1L)",
      staticMethodInvocationsChecking = false,
      methodExecutionForUnknownAllowed = true
    ).validExpression.evaluateSync[JBigInteger](ctx) should equal(JBigInteger.ONE)
  }

  test("blocking excluded in runtime, allowed reference") {
    parse[Long]("T(java.lang.Long).valueOf(1L)").validExpression.evaluateSync[Long](ctx) should equal(1L)
  }

  test("evaluate call on non-existing static method of validated class String") {
    inside(parse[Any]("T(java.lang.String).copyValueOf({'t', 'e', 's', 't'})")) {
      case Invalid(NonEmptyList(error: UnknownMethodError, Nil)) =>
        error.message shouldBe "Unknown method 'copyValueOf' in String"
    }
  }

  test("evaluate static method call on validated class Integer") {
    parse[Int]("T(java.lang.Integer).min(1, 2)").validExpression.evaluateSync[Int](ctx) should equal(1)
  }

  test("evaluate static method call on unvalidated class") {
    inside(parse[Any]("T(java.lang.System).exit()")) { case Invalid(NonEmptyList(error: TypeReferenceError, _)) =>
      error.message shouldBe "class java.lang.System is not allowed to be passed as TypeReference"
    }
  }

  test("evaluate static method call on non-existing class") {
    inside(parse[Any]("T(java.lang.NonExistingClass).method()")) {
      case Invalid(NonEmptyList(error: UnknownClassError, _)) =>
        error.message shouldBe "Class java.lang.NonExistingClass does not exist"
    }
  }

  test("not throw an exception when is used lower case type") {
    parse[Any]("T(foo).bar", ctx) should matchPattern { case Invalid(NonEmptyList(UnknownClassError("foo"), _)) =>
    }
  }

  test("invoke simple expression") {
    parse[java.lang.Number]("#obj.value + 4").validExpression.evaluateSync[Long](ctx) should equal(6)
  }

  test("invoke simple list expression") {
    parse[Boolean]("{'1', '2'}.contains('2')").validExpression.evaluateSync[Boolean](ctx) shouldBe true
  }

  test("handle string concatenation correctly") {
    parse[String]("'' + 1") shouldBe Symbol("valid")
    parse[Long]("2 + 1") shouldBe Symbol("valid")
    parse[String]("'' + ''") shouldBe Symbol("valid")
    parse[String]("4 + ''") shouldBe Symbol("valid")
  }

  test("subtraction of non numeric types") {
    inside(parse[Any]("'a' - 'a'")) { case Invalid(NonEmptyList(error: OperatorNonNumericError, Nil)) =>
      error.message shouldBe s"Operator '-' used with non numeric type: ${Typed.fromInstance("a").display}"
    }
  }

  test("substraction of mismatched types") {
    inside(parse[Any]("'' - 1")) { case Invalid(NonEmptyList(error: OperatorMismatchTypeError, Nil)) =>
      error.message shouldBe s"Operator '-' used with mismatch types: ${Typed.fromInstance("").display} and ${Typed.fromInstance(1).display}"
    }
  }

  test("use not existing method reference") {
    inside(parse[Any]("notExistingMethod(1)", ctxWithGlobal)) {
      case Invalid(NonEmptyList(error: InvalidMethodReference, _)) =>
        error.message shouldBe "Invalid method reference: notExistingMethod(1)."
    }
  }

  test("null properly") {
    parse[String]("null") shouldBe Symbol("valid")
    parse[Long]("null") shouldBe Symbol("valid")
    parse[Any]("null") shouldBe Symbol("valid")
    parse[Boolean]("null") shouldBe Symbol("valid")

    parse[Any]("null").toOption.get.returnType shouldBe TypedNull
    parse[java.util.List[String]]("{'t', null, 'a'}").toOption.get.returnType shouldBe
      typedListWithElementValues(Typed[String], List("t", null, "a").asJava)
    parse[java.util.List[Any]]("{5, 't', null}").toOption.get.returnType shouldBe
      typedListWithElementValues(Typed[Any], List(5, "t", null).asJava)
    parse[Int]("true ? 8 : null").toOption.get.returnType shouldBe Typed[Int]
  }

  test("invoke list variable reference with different concrete type after compilation") {
    def contextWithList(value: Any) = ctx.withVariable("list", value)
    val expr                        = parse[Any]("#list", contextWithList(Collections.emptyList())).validExpression

    // first run - nothing happens, we bump the counter
    expr.evaluateSync[Any](contextWithList(null))
    // second run - exitTypeDescriptor is set, expression is compiled
    expr.evaluateSync[Any](contextWithList(new util.ArrayList[String]()))
    // third run - expression is compiled as ArrayList and we fail :(
    expr.evaluateSync[Any](contextWithList(Collections.emptyList()))
  }

  test("perform date operations") {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays   = ctx.withVariable("date", twoDaysAgo)
    parse[Any]("#date.until(T(java.time.LocalDate).now()).days", withDays).validExpression
      .evaluateSync[Integer](withDays) should equal(2)
  }

  test("register functions") {
    val twoDaysAgo = LocalDate.now().minusDays(2)
    val withDays = ctx
      .withVariable("date", twoDaysAgo)
      .withVariable("today", classOf[LocalDate].getDeclaredMethod("now"))
    parse[Any]("#date.until(#today()).days", withDays).validExpression.evaluateSync[Integer](withDays) should equal(2)
  }

  test("be possible to use SpEL's #this object") {
    parse[Any]("{1, 2, 3}.?[ #this > 1]").validExpression
      .evaluateSync[java.util.List[Integer]](ctx) shouldBe util.Arrays.asList(2, 3)
    parse[Any]("{1, 2, 3}.![ #this > 1]").validExpression
      .evaluateSync[java.util.List[Boolean]](ctx) shouldBe util.Arrays.asList(false, true, true)
    parse[Any]("{'1', '22', '3'}.?[ #this.length > 1]").validExpression
      .evaluateSync[java.util.List[Boolean]](ctx) shouldBe util.Arrays.asList("22")
    parse[Any]("{'1', '22', '3'}.![ #this.length > 1]").validExpression
      .evaluateSync[java.util.List[Boolean]](ctx) shouldBe util.Arrays.asList(false, true, false)
  }

  test("validate MethodReference") {
    parse[Any]("#processHelper.add(1, 1)", ctxWithGlobal).isValid shouldBe true
    inside(parse[Any]("#processHelper.addT(1, 1)", ctxWithGlobal)) {
      case Invalid(NonEmptyList(error: UnknownMethodError, Nil)) =>
        error.message shouldBe "Unknown method 'addT' in SampleGlobalObject"
    }
  }

  test("validate MethodReference parameter types") {
    parse[Any]("#processHelper.add(1, 1)", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#processHelper.add(1L, 1)", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#processHelper.addLongs(1L, 1L)", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#processHelper.addLongs(1, 1L)", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#processHelper.add(#processHelper.toAny('1'), 1)", ctxWithGlobal) shouldBe Symbol("valid")

    inside(parse[Any]("#processHelper.add('1', 1)", ctxWithGlobal)) {
      case Invalid(NonEmptyList(error: ArgumentTypeError, Nil)) =>
        error.message shouldBe s"Mismatch parameter types. Found: add(${Typed.fromInstance("1").display}, ${Typed.fromInstance(1).display}). Required: add(Integer, Integer)"
    }
  }

  test("validate MethodReference for scala varargs") {
    parse[Any]("#processHelper.addAll()", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#processHelper.addAll(1)", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#processHelper.addAll(1, 2, 3)", ctxWithGlobal) shouldBe Symbol("valid")
  }

  test("validate MethodReference for java varargs") {
    parse[Any]("#javaClassWithVarargs.addAll()", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#javaClassWithVarargs.addAll(1)", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Any]("#javaClassWithVarargs.addAll(1, 2, 3)", ctxWithGlobal) shouldBe Symbol("valid")
  }

  test("evaluate MethodReference for scala varargs") {
    parse[Any]("#processHelper.addAll()", ctxWithGlobal).validExpression.evaluateSync[Any](ctxWithGlobal) shouldBe 0
    parse[Any]("#processHelper.addAll(1)", ctxWithGlobal).validExpression.evaluateSync[Any](ctxWithGlobal) shouldBe 1
    parse[Any]("#processHelper.addAll(1, 2, 3)", ctxWithGlobal).validExpression
      .evaluateSync[Any](ctxWithGlobal) shouldBe 6
  }

  test("evaluate MethodReference for java varargs") {
    parse[Any]("#javaClassWithVarargs.addAll()", ctxWithGlobal).validExpression
      .evaluateSync[Any](ctxWithGlobal) shouldBe 0
    parse[Any]("#javaClassWithVarargs.addAll(1)", ctxWithGlobal).validExpression
      .evaluateSync[Any](ctxWithGlobal) shouldBe 1
    parse[Any]("#javaClassWithVarargs.addAll(1, 2, 3)", ctxWithGlobal).validExpression
      .evaluateSync[Any](ctxWithGlobal) shouldBe 6
  }

  test("skip MethodReference validation without strictMethodsChecking") {
    val parsed = parse[Any]("#processHelper.notExistent(1, 1)", ctxWithGlobal, strictMethodsChecking = false)
    parsed.isValid shouldBe true
  }

  test("return invalid type for MethodReference with invalid arity ") {
    val parsed = parse[Any]("#processHelper.add(1)", ctxWithGlobal)
    val expectedValidation = Invalid(
      s"Mismatch parameter types. Found: add(${Typed.fromInstance(1).display}). Required: add(Integer, Integer)"
    )
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  test("return invalid type for MethodReference with missing arguments") {
    val parsed             = parse[Any]("#processHelper.add()", ctxWithGlobal)
    val expectedValidation = Invalid("Mismatch parameter types. Found: add(). Required: add(Integer, Integer)")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  test("return invalid type if PropertyOrFieldReference does not exists") {
    val parsed             = parse[Any]("#processHelper.unknownProperty", ctxWithGlobal)
    val expectedValidation = Invalid("There is no property 'unknownProperty' in type: SampleGlobalObject")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  test("return invalid argument if arguments are not passed to method") {
    val parsed             = parse[Any]("#processHelper.add", ctxWithGlobal)
    val expectedValidation = Invalid("Mismatch parameter types. Found: add(). Required: add(Integer, Integer)")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  test("handle big decimals") {
    bigValue.compareTo(JBigDecimal.valueOf(50 * 1024 * 1024)) should be > 0
    bigValue.compareTo(JBigDecimal.valueOf(50 * 1024 * 1024L)) should be > 0
    parse[Any]("#obj.bigValue").validExpression.evaluateSync[JBigDecimal](ctx) should equal(bigValue)
    parse[Boolean]("#obj.bigValue < 50*1024*1024").validExpression.evaluateSync[Boolean](ctx) should equal(false)
    parse[Boolean]("#obj.bigValue < 50*1024*1024L").validExpression.evaluateSync[Boolean](ctx) should equal(false)
  }

  test("access list elements by index") {
    parse[String]("#obj.children[0].id").validExpression.evaluateSync[String](ctx) shouldEqual "3"
    parse[Int]("#obj.children[0].id") shouldBe Symbol("invalid")
  }

  test("access fields with the same name as a no parameter method in record") {
    parse[String]("{getClass: 'str'}.getClass").validExpression.evaluateSync[String](ctx) shouldEqual "str"
    parse[String]("{isEmpty: 'str'}.isEmpty").validExpression.evaluateSync[String](ctx) shouldEqual "str"
  }

  test("access fields with the name of method without getter prefix in record") {
    // Reflective accessor would normally try to find methods with added getter prefixes "is" or "get" so that ".class"
    // would call ".getClass()"
    parse[String]("{class: 'str'}.class").validExpression.evaluateSync[String](ctx) shouldEqual "str"
    parse[String]("{empty: 'str'}.empty").validExpression.evaluateSync[String](ctx) shouldEqual "str"
  }

  test("access record elements by index") {
    val ctxWithVal = ctx.withVariable("stringKey", "string")
    val testRecordExpr: String =
      "{int: 1, string: 'stringVal', boolean: true, 'null': null, nestedRecord: {nestedRecordKey: 2}, nestedList: {1,2,3}}"

    parse[String](s"$testRecordExpr['string']").validExpression.evaluateSync[String](ctx) shouldBe "stringVal"
    parse[String](s"$testRecordExpr[string]").validExpression.evaluateSync[String](ctx) shouldBe "stringVal"
    parse[String](s"$testRecordExpr['str' + 'ing']").validExpression.evaluateSync[String](ctx) shouldBe "stringVal"
    parse[String](s"$testRecordExpr[#stringKey]", ctxWithVal).validExpression
      .evaluateSync[String](ctxWithVal) shouldBe "stringVal"

    parse[Any](s"$testRecordExpr['nestedRecord']").validExpression
      .evaluateSync[Any](ctx) shouldBe Collections.singletonMap("nestedRecordKey", 2)
    parse[Any](s"$testRecordExpr[nestedRecord]").validExpression
      .evaluateSync[Any](ctx) shouldBe Collections.singletonMap("nestedRecordKey", 2)

    parse[Int](s"$testRecordExpr[nestedRecord][nestedRecordKey]").validExpression.evaluateSync[Int](ctx) shouldBe 2
    parse[Int](s"$testRecordExpr['nestedRecord']['nestedRecordKey']").validExpression.evaluateSync[Int](ctx) shouldBe 2

    parse[Any](s"$testRecordExpr[nestedList]").validExpression
      .evaluateSync[Any](ctx) shouldBe util.Arrays.asList(1, 2, 3)
    parse[Any](s"$testRecordExpr['nestedList']").validExpression
      .evaluateSync[Any](ctx) shouldBe util.Arrays.asList(1, 2, 3)
  }

  test("should return no property present error for record non-present element when enabled") {
    inside(parse[Any]("{key: 1}[nonPresentField]")) {
      case Invalid(l: NonEmptyList[ExpressionParseError])
          if l.toList.exists(error => error.message.startsWith("There is no property 'nonPresentField' in type:")) =>
    }
    inside(parse[Any]("{key: 1}['nonPresentField']")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message should startWith("There is no property 'nonPresentField' in type:")
    }
  }

  test("should return null for dynamic property access for record non-present element when enabled") {
    parse[Any](s"{key: 1}['nonPresentField']", dynamicPropertyAccessAllowed = true).validExpression
      .evaluateSync[Any](ctx) == null shouldBe true
  }

  test("filter by list predicates") {

    parse[Any]("#obj.children.?[id == '55'].isEmpty").validExpression.evaluateSync[Boolean](ctx) should equal(true)
    parse[Any]("#obj.children.?[id == '55' || id == '66'].isEmpty").validExpression
      .evaluateSync[Boolean](ctx) should equal(true)
    parse[Any]("#obj.children.?[id == '5'].size()").validExpression.evaluateSync[Integer](ctx) should equal(1: Integer)
    parse[Any]("#obj.children.?[id == '5' || id == '3'].size()").validExpression
      .evaluateSync[Integer](ctx) should equal(2: Integer)
    parse[Any]("#obj.children.?[id == '5' || id == '3'].![value]").validExpression
      .evaluateSync[util.ArrayList[Long]](ctx) should equal(new util.ArrayList(util.Arrays.asList(4L, 6L)))
    parse[Any]("(#obj.children.?[id == '5' || id == '3'].![value]).contains(4L)").validExpression
      .evaluateSync[Boolean](ctx) should equal(true)

  }

  test("accumulate errors") {
    parse[Any]("#obj.children.^[id == 123].foo").invalidValue.toList should matchPattern {
      case OperatorNotComparableError("==", _, _) :: NoPropertyError(childrenType, "foo") :: Nil
          if childrenType == Typed[Test] =>
    }
  }

  test("evaluate map") {
    val ctxWithVar = ctx.withVariable("processVariables", Collections.singletonMap("processingStartTime", 11L))
    parse[Any](
      "#processVariables['processingStartTime']",
      ctxWithVar,
      dynamicPropertyAccessAllowed = true
    ).validExpression.evaluateSync[Long](ctxWithVar) should equal(11L)
  }

  test("stop validation when property of Any/Object type found") {
    val ctxWithVar = ctx.withVariable("obj", SampleValue(11))
    parse[Any](
      "#obj.anyObject.anyPropertyShouldValidate",
      ctxWithVar,
      methodExecutionForUnknownAllowed = true
    ) shouldBe Symbol("valid")
  }

  test("allow empty expression") {
    parse[Any]("", ctx) shouldBe Symbol("valid")
  }

  test("register static variables") {
    parse[Any]("#processHelper.add(1, #processHelper.constant())", ctxWithGlobal).validExpression
      .evaluateSync[Integer](ctxWithGlobal) should equal(5)
  }

  test("allow access to maps in dot notation") {
    val withMapVar = ctx.withVariable("map", Map("key1" -> "value1", "key2" -> 20).asJava)

    parse[String]("#map.key1", withMapVar).validExpression.evaluateSync[String](withMapVar) should equal("value1")
    parse[Integer]("#map.key2", withMapVar).validExpression.evaluateSync[Integer](withMapVar) should equal(20)
  }

  test("missing keys in Maps") {
    val validationCtx = ValidationContext.empty
      .withVariable(
        "map",
        Typed.record(
          Map(
            "foo"    -> Typed[Int],
            "nested" -> Typed.record(Map("bar" -> Typed[Int]))
          )
        ),
        paramName = None
      )
      .toOption
      .get
    val ctxWithMap = ctx.withVariable("map", Collections.emptyMap())
    parseV[Integer]("#map.foo", validationCtx).validExpression.evaluateSync[Integer](ctxWithMap) shouldBe null
    parseV[Integer]("#map.nested?.bar", validationCtx).validExpression.evaluateSync[Integer](ctxWithMap) shouldBe null
    parseV[Boolean]("#map.foo == null && #map?.nested?.bar == null", validationCtx).validExpression
      .evaluateSync[Boolean](ctxWithMap) shouldBe true

    val ctxWithTypedMap = ctx.withVariable("map", TypedMap(Map.empty))
    val parseResult     = parseV[Integer]("#map.foo", validationCtx).validExpression
    parseResult.evaluateSync[Integer](ctxWithTypedMap) shouldBe null
  }

  test("check return type for map property accessed in dot notation") {
    parse[String]("#processHelper.stringOnStringMap.key1", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Integer]("#processHelper.stringOnStringMap.key1", ctxWithGlobal) shouldBe Symbol("invalid")
  }

  test("allow access to objects with get method in dot notation") {
    val withObjVar = ctx.withVariable("obj", new SampleObjectWithGetMethod(Map("key1" -> "value1", "key2" -> 20)))

    parse[String]("#obj.key1", withObjVar).validExpression.evaluateSync[String](withObjVar) should equal("value1")
    parse[Integer]("#obj.key2", withObjVar).validExpression.evaluateSync[Integer](withObjVar) should equal(20)
  }

  test("check property if is defined even if class has get method") {
    val withObjVar = ctx.withVariable("obj", new SampleObjectWithGetMethod(Map.empty))

    parse[Boolean]("#obj.definedProperty == 123", withObjVar) shouldBe Symbol("invalid")
    parse[Boolean]("#obj.definedProperty == '123'", withObjVar).validExpression
      .evaluateSync[Boolean](withObjVar) shouldBe true
  }

  test("check property if is defined even if class has get method - avro generic record") {
    val schema = new Schema.Parser().parse("""{
        |  "type": "record",
        |  "name": "Foo",
        |  "fields": [
        |    { "name": "text", "type": "string" }
        |  ]
        |}
      """.stripMargin)
    val record = new GenericData.Record(schema)
    record.put("text", "foo")
    val withObjVar = ctx.withVariable("obj", record)
    parse[String]("#obj.text", withObjVar).validExpression.evaluateSync[String](withObjVar) shouldEqual "foo"
  }

  test("allow access to statics") {
    val withMapVar = ctx.withVariable("longClass", classOf[java.lang.Long])
    parse[Any]("#longClass.valueOf('44')", withMapVar).validExpression
      .evaluateSync[Long](withMapVar) should equal(44L)

    parse[Any]("T(java.lang.Long).valueOf('44')", ctx).validExpression
      .evaluateSync[Long](ctx) should equal(44L)
  }

  test("should != correctly for compiled expression - expression is compiled when invoked for the 3rd time") {
    // see https://jira.spring.io/browse/SPR-9194 for details
    val empty      = new String("")
    val withMapVar = ctx.withVariable("emptyStr", empty)

    val expression = parse[Boolean]("#emptyStr != ''", withMapVar).validExpression
    expression.evaluateSync[Boolean](withMapVar) should equal(false)
    expression.evaluateSync[Boolean](withMapVar) should equal(false)
    expression.evaluateSync[Boolean](withMapVar) should equal(false)
  }

  test("not allow access to variables without hash in methods") {
    val withNum = ctx.withVariable("a", 5).withVariable("processHelper", SampleGlobalObject)
    inside(parse[Any]("#processHelper.add(a, 1)", withNum)) {
      case Invalid(l: NonEmptyList[ExpressionParseError @unchecked])
          if l.toList
            .exists(error => error.message == "Non reference 'a' occurred. Maybe you missed '#' in front of it?") =>
    }
  }

  test("not allow unknown variables in methods") {
    inside(parse[Any]("#processHelper.add(#a, 1)", ctx.withVariable("processHelper", SampleGlobalObject))) {
      case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
        error.message shouldBe "Unresolved reference 'a'"
    }

    inside(parse[Any]("T(java.text.NumberFormat).getNumberInstance('PL').format(#a)", ctx)) {
      case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
        error.message shouldBe "Unresolved reference 'a'"
    }
  }

  test("not allow vars without hashes in equality condition") {
    inside(parse[Any]("nonexisting == 'ala'", ctx)) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe "Non reference 'nonexisting' occurred. Maybe you missed '#' in front of it?"
    }
  }

  test("validate simple literals") {
    parse[Long]("-1", ctx) shouldBe Symbol("valid")
    parse[Float]("-1.1", ctx) shouldBe Symbol("valid")
    parse[Long]("-1.1", ctx) should not be Symbol("valid")
    parse[Double]("-1.1", ctx) shouldBe Symbol("valid")
    parse[java.math.BigDecimal]("-1.1", ctx) shouldBe Symbol("valid")
  }

  test("should not validate map indexing if index type and map key type are different") {
    parse[Any]("""{{key: "a", value: 5}}.toMap[0]""") shouldBe Symbol("invalid")
    parse[Any]("""{{key: 1, value: 5}}.toMap["0"]""") shouldBe Symbol("invalid")
    parse[Any]("""{{key: 1.toLong, value: 5}}.toMap[0]""") shouldBe Symbol("invalid")
    parse[Any]("""{{key: 1, value: 5}}.toMap[0.toLong]""") shouldBe Symbol("invalid")
  }

  test("should validate map indexing if index type and map key type are the same") {
    parse[Any]("""{{key: 1, value: 5}}.toMap[0]""") shouldBe Symbol("valid")
  }

  test("should handle map indexing with unknown key type") {
    val context = Context("sth").withVariables(
      Map(
        "unknownString" -> ContainerOfUnknown("a"),
      )
    )

    evaluate[Int]("""{{key: "a", value: 5}}.toMap[#unknownString.value]""", context) shouldBe 5
    evaluate[Integer]("""{{key: "b", value: 5}}.toMap[#unknownString.value]""", context) shouldBe null
  }

  test("validate ternary operator") {
    parse[Long]("'d'? 3 : 4", ctx) should not be Symbol("valid")
    parse[String]("1 > 2 ? 12 : 23", ctx) should not be Symbol("valid")
    parse[Long]("1 > 2 ? 12 : 23", ctx).validValue.returnType shouldBe Typed[Integer]
    parse[Number]("1 > 2 ? 12 : 23.0", ctx).validValue.returnType shouldBe Typed[Number]
    parse[String]("1 > 2 ? 'ss' : 'dd'", ctx).validValue.returnType shouldBe Typed[String]
    parse[Any]("1 > 2 ? '123' : 123", ctx).validValue.returnType shouldBe Unknown
    parse[Any]("1 > 2 ? {foo: 1} : {bar: 1}", ctx).validValue.returnType shouldBe Typed.record(
      Map(
        "foo" -> Typed.fromInstance(1),
        "bar" -> Typed.fromInstance(1),
      )
    )
    parse[Any](
      "1 > 2 ? {foo: 1} : #processHelper.stringOnStringMap",
      ctxWithGlobal
    ).validValue.returnType shouldBe Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown))
  }

  test("validate selection for inline list") {
    parse[Long]("{44, 44}.?[#this.alamakota]", ctx) should not be Symbol("valid")
    parse[java.util.List[_]]("{44, 44}.?[#this > 4]", ctx) shouldBe Symbol("valid")
  }

  test("validate selection and projection for list variable") {
    val vctx = ValidationContext.empty
      .withVariable("a", Typed.fromDetailedType[java.util.List[String]], paramName = None)
      .toOption
      .get

    parseV[java.util.List[Int]]("#a.![#this.length()].?[#this > 4]", vctx) shouldBe Symbol("valid")
    parseV[java.util.List[Boolean]]("#a.![#this.length()].?[#this > 4]", vctx) shouldBe Symbol("invalid")
    parseV[java.util.List[Int]]("#a.![#this / 5]", vctx) should not be Symbol("valid")
  }

  test("allow #this reference inside functions") {
    parse[java.util.List[String]]("{1, 2, 3}.!['ala'.substring(#this - 1)]", ctx).validExpression
      .evaluateSync[java.util.List[String]](ctx)
      .asScala
      .toList shouldBe List("ala", "la", "a")
  }

  test("allow property access in unknown classes") {
    parseV[Any]("#input.anyObject", ValidationContext(Map("input" -> Typed[SampleValue]))) shouldBe Symbol("valid")
  }

  test("validate expression with projection and filtering") {
    val ctxWithInput = ctx.withVariable("input", SampleObject(util.Arrays.asList(SampleValue(444))))
    parse[Any]("(#input.list.?[value == 5]).![value].contains(5)", ctxWithInput) shouldBe Symbol("valid")
  }

  test("validate map literals") {
    val ctxWithInput = ctx.withVariable("input", SampleValue(444))
    parse[Any]("{ Field1: 'Field1Value', Field2: 'Field2Value', Field3: #input.value }", ctxWithInput) shouldBe Symbol(
      "valid"
    )
  }

  test("validate list literals") {
    parse[Int]("#processHelper.stringList({})", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Int]("#processHelper.stringList({'aa'})", ctxWithGlobal) shouldBe Symbol("valid")
    parse[Int]("#processHelper.stringList({333})", ctxWithGlobal) shouldNot be(Symbol("valid"))
  }

  test("type map literals") {
    val ctxWithInput = ctx.withVariable("input", SampleValue(444))
    parse[Any]("{ Field1: 'Field1Value', Field2: #input.value }.Field1", ctxWithInput) shouldBe Symbol("valid")
    parse[Any]("{ Field1: 'Field1Value', 'Field2': #input }.Field2.value", ctxWithInput) shouldBe Symbol("valid")
    parse[Any]("{ Field1: 'Field1Value', Field2: #input }.noField", ctxWithInput) shouldNot be(Symbol("valid"))

  }

  test("not validate plain string ") {
    parse[Any]("abcd", ctx) shouldNot be(Symbol("valid"))
  }

  test("can handle return generic return types") {
    parse[Any]("#processHelper.now.toLocalDate", ctxWithGlobal).map(_.returnType) should be(Valid(Typed[LocalDate]))
  }

  test("evaluate static field/method using property syntax") {
    parse[Any]("#processHelper.one", ctxWithGlobal).validExpression.evaluateSync[Int](ctxWithGlobal) should equal(1)
    parse[Any]("#processHelper.one()", ctxWithGlobal).validExpression.evaluateSync[Int](ctxWithGlobal) should equal(1)
    parse[Any]("#processHelper.constant", ctxWithGlobal).validExpression.evaluateSync[Int](ctxWithGlobal) should equal(
      4
    )
    parse[Any]("#processHelper.constant()", ctxWithGlobal).validExpression
      .evaluateSync[Int](ctxWithGlobal) should equal(4)
  }

  test("detect bad type of literal or variable") {

    def shouldHaveBadType(valid: Validated[NonEmptyList[ExpressionParseError], _], message: String) =
      inside(valid) { case Invalid(NonEmptyList(error: ExpressionTypeError, _)) =>
        error.message shouldBe message
      }

    shouldHaveBadType(
      parse[Int]("'abcd'", ctx),
      s"Bad expression type, expected: Integer, found: ${Typed.fromInstance("abcd").display}"
    )
    shouldHaveBadType(
      parse[String]("111", ctx),
      s"Bad expression type, expected: String, found: ${Typed.fromInstance(111).display}"
    )
    shouldHaveBadType(
      parse[String]("{1, 2, 3}", ctx),
      s"Bad expression type, expected: String, found: ${typedListWithElementValues(Typed[Int], List(1, 2, 3).asJava).display}"
    )
    shouldHaveBadType(
      parse[java.util.Map[_, _]]("'alaMa'", ctx),
      s"Bad expression type, expected: Map[Unknown,Unknown], found: ${Typed.fromInstance("alaMa").display}"
    )
    shouldHaveBadType(
      parse[Int]("#strVal", ctx),
      s"Bad expression type, expected: Integer, found: ${Typed.fromInstance("").display}"
    )
  }

  test("resolve imported package") {
    val givenValue = 123
    parse[SampleValue](s"new SampleValue($givenValue, '')").validExpression.evaluateSync[SampleValue](ctx) should equal(
      SampleValue(givenValue)
    )
  }

  test("parseV typed map with existing field") {
    val ctxWithMap = ValidationContext.empty
      .withVariable(
        "input",
        Typed.record(Map("str" -> Typed[String], "lon" -> Typed[Long])),
        paramName = None
      )
      .toOption
      .get

    parseV[String]("#input.str", ctxWithMap) should be(Symbol("valid"))
    parseV[Long]("#input.lon", ctxWithMap) should be(Symbol("valid"))

    parseV[Long]("#input.str", ctxWithMap) shouldNot be(Symbol("valid"))
    parseV[String]("#input.ala", ctxWithMap) shouldNot be(Symbol("valid"))
  }

  test("be able to convert between primitive types") {
    val ctxWithMap = ValidationContext.empty
      .withVariable("input", Typed.record(Map("int" -> Typed[Int])), paramName = None)
      .toOption
      .get

    val ctx = Context("").withVariable("input", TypedMap(Map("int" -> 1)))

    parseV[Long]("#input.int.longValue", ctxWithMap).validExpression.evaluateSync[Long](ctx) shouldBe 1L
  }

  test("evaluate parsed map") {
    val valCtxWithMap = ValidationContext.empty
      .withVariable(
        "input",
        Typed.record(Map("str" -> Typed[String], "lon" -> Typed[Long])),
        paramName = None
      )
      .toOption
      .get

    val ctx = Context("").withVariable("input", TypedMap(Map("str" -> "aaa", "lon" -> 3444)))

    parseV[String]("#input.str", valCtxWithMap).validExpression.evaluateSync[String](ctx) shouldBe "aaa"
    parseV[Long]("#input.lon", valCtxWithMap).validExpression.evaluateSync[Long](ctx) shouldBe 3444
    parseV[Any]("#input.notExisting", valCtxWithMap) shouldBe Symbol("invalid")
    parseV[Boolean]("#input.containsValue('aaa')", valCtxWithMap).validExpression
      .evaluateSync[Boolean](ctx) shouldBe true
    parseV[Int]("#input.size", valCtxWithMap).validExpression.evaluateSync[Int](ctx) shouldBe 2
    parseV[Boolean]("#input == {str: 'aaa', lon: 3444}", valCtxWithMap).validExpression
      .evaluateSync[Boolean](ctx) shouldBe true
  }

  test("be able to type toString()") {
    parse[Any]("12.toString()", ctx).toOption.get.returnType shouldBe Typed[String]
  }

  test("expand all fields of TypedObjects in union") {
    val ctxWithMap = ValidationContext.empty
      .withVariable(
        "input",
        Typed(Typed.record(Map("str" -> Typed[String])), Typed.record(Map("lon" -> Typed[Long]))),
        paramName = None
      )
      .toOption
      .get

    parseV[String]("#input.str", ctxWithMap) should be(Symbol("valid"))
    parseV[Long]("#input.lon", ctxWithMap) should be(Symbol("valid"))

    parseV[Long]("#input.str", ctxWithMap) shouldNot be(Symbol("valid"))
    parseV[String]("#input.ala", ctxWithMap) shouldNot be(Symbol("valid"))
  }

  test("expand all fields of TypedClass in union") {
    val ctxWithMap = ValidationContext.empty
      .withVariable("input", Typed(Typed[SampleObject], Typed[SampleValue]), paramName = None)
      .toOption
      .get

    parseV[java.util.List[SampleValue]]("#input.list", ctxWithMap) should be(Symbol("valid"))
    parseV[Int]("#input.value", ctxWithMap) should be(Symbol("valid"))

    parseV[Set[_]]("#input.list", ctxWithMap) shouldNot be(Symbol("valid"))
    parseV[String]("#input.value", ctxWithMap) shouldNot be(Symbol("valid"))
  }

  test("parses expression with template context") {
    parse[String]("alamakota #{444}", ctx, flavour = SpelExpressionParser.Template) shouldBe Symbol("valid")
    parse[String]("alamakota #{444 + #obj.value}", ctx, flavour = SpelExpressionParser.Template) shouldBe Symbol(
      "valid"
    )
    parse[String]("alamakota #{444 + #nothing}", ctx, flavour = SpelExpressionParser.Template) shouldBe Symbol(
      "invalid"
    )
    parse[String]("#{'raz'},#{'dwa'}", ctx, flavour = SpelExpressionParser.Template) shouldBe Symbol("valid")
    parse[String]("#{'raz'},#{12345}", ctx, flavour = SpelExpressionParser.Template) shouldBe Symbol("valid")
  }

  test("evaluates expression with template context") {
    parse[String]("alamakota #{444}", ctx, flavour = SpelExpressionParser.Template).validExpression
      .evaluateSync[TemplateEvaluationResult](skipReturnTypeCheck = true)
      .renderedTemplate shouldBe "alamakota 444"
    parse[String](
      "alamakota #{444 + #obj.value} #{#mapValue.foo}",
      ctx,
      flavour = SpelExpressionParser.Template
    ).validExpression
      .evaluateSync[TemplateEvaluationResult](skipReturnTypeCheck = true)
      .renderedTemplate shouldBe "alamakota 446 bar"
  }

  test("evaluates empty template as empty string") {
    parse[String]("", ctx, flavour = SpelExpressionParser.Template).validExpression
      .evaluateSync[TemplateEvaluationResult](skipReturnTypeCheck = true)
      .renderedTemplate shouldBe ""
  }

  test("variables with TypeMap type") {
    val withObjVar = ctx.withVariable("dicts", TypedMap(Map("foo" -> SampleValue(123))))

    parse[Int]("#dicts.foo.value", withObjVar).validExpression.evaluateSync[Int](withObjVar) should equal(123)
    parse[String]("#dicts.bar.value", withObjVar) shouldBe Symbol("invalid")
  }

  test("adding invalid type to number") {
    val floatAddExpr = "12.1 + #obj"
    parse[Float](floatAddExpr, ctx) shouldBe Symbol("invalid")
  }

  test("different types in equality") {
    parse[Boolean]("'123' == 234", ctx) shouldBe Symbol("invalid")
    parse[Boolean]("'123' == '234'", ctx) shouldBe Symbol("valid")
    parse[Boolean]("'123' == null", ctx) shouldBe Symbol("valid")

    parse[Boolean]("'123' != 234", ctx) shouldBe Symbol("invalid")
    parse[Boolean]("'123' != '234'", ctx) shouldBe Symbol("valid")
    parse[Boolean]("'123' != null", ctx) shouldBe Symbol("valid")

    parse[Boolean]("123 == 123123123123L", ctx) shouldBe Symbol("valid")
    parse[Boolean]("{1, 2} == {'a', 'b'}", ctx) shouldBe Symbol("invalid")
    // Number's have common Number supertype
    parse[Boolean]("{1, 2} == {1.0, 2.0}", ctx) shouldBe Symbol("valid")
  }

  test("compare records with different fields in equality") {
    parse[Boolean]("{foo: null} == {:}", ctx) shouldBe Symbol("valid")
    parse[Boolean](
      "{key1: 'value1', key2: 'value2'} == #processHelper.stringOnStringMap",
      ctxWithGlobal
    ).validExpression.evaluateSync[Boolean](ctxWithGlobal) shouldBe true
  }

  test("precise type parsing in two operand operators") {
    val floatAddExpr = "12.1 + 23.4"
    parse[Int](floatAddExpr, ctx) shouldBe Symbol("invalid")
    parse[Float](floatAddExpr, ctx) shouldBe Symbol("valid")
    parse[java.lang.Float](floatAddExpr, ctx) shouldBe Symbol("valid")
    parse[Double](floatAddExpr, ctx) shouldBe Symbol("valid")

    val floatMultiplyExpr = "12.1 * 23.4"
    parse[Int](floatMultiplyExpr, ctx) shouldBe Symbol("invalid")
    parse[Float](floatMultiplyExpr, ctx) shouldBe Symbol("valid")
    parse[java.lang.Float](floatMultiplyExpr, ctx) shouldBe Symbol("valid")
    parse[Double](floatMultiplyExpr, ctx) shouldBe Symbol("valid")
  }

  test("precise type parsing in single operand operators") {
    val floatAddExpr = "12.1++"
    parse[Int](floatAddExpr, ctx) shouldBe Symbol("invalid")
    parse[Float](floatAddExpr, ctx) shouldBe Symbol("valid")
    parse[java.lang.Float](floatAddExpr, ctx) shouldBe Symbol("valid")
    parse[Double](floatAddExpr, ctx) shouldBe Symbol("valid")
  }

  test("embedded dict values") {
    val embeddedDictId = "embeddedDictId"
    val dicts          = Map(embeddedDictId -> EmbeddedDictDefinition(Map("fooId" -> "fooLabel")))
    val withObjVar     = ctx.withVariable("embeddedDict", DictInstance(embeddedDictId, dicts(embeddedDictId)))

    parse[String]("#embeddedDict['fooId']", withObjVar, dicts).toOption.get.expression
      .evaluateSync[String](withObjVar) shouldEqual "fooId"
    parse[String]("#embeddedDict['wrongId']", withObjVar, dicts) shouldBe Symbol("invalid")
  }

  test("enum dict values") {
    val enumDictId = EmbeddedDictDefinition.enumDictId(classOf[SimpleEnum.SimpleValue])
    val dicts = Map(
      enumDictId -> EmbeddedDictDefinition
        .forScalaEnum[SimpleEnum.type](SimpleEnum)
        .withValueClass[SimpleEnum.SimpleValue]
    )
    val withObjVar = ctx
      .withVariable("stringValue", "one")
      .withVariable("enumValue", SimpleEnum.One)
      .withVariable("enum", DictInstance(enumDictId, dicts(enumDictId)))

    parse[SimpleEnum.SimpleValue]("#enum['one']", withObjVar, dicts).toOption.get.expression
      .evaluateSync[SimpleEnum.SimpleValue](withObjVar) shouldEqual SimpleEnum.One
    parse[SimpleEnum.SimpleValue]("#enum['wrongId']", withObjVar, dicts) shouldBe Symbol("invalid")

    parse[Boolean]("#enumValue == #enum['one']", withObjVar, dicts).toOption.get.expression
      .evaluateSync[Boolean](withObjVar) shouldBe true
    parse[Boolean]("#stringValue == #enum['one']", withObjVar, dicts) shouldBe Symbol("invalid")
  }

  test("should be able to call generic functions") {
    parse[Int]("#processHelper.genericFunction(8, false)", ctxWithGlobal).validExpression
      .evaluateSync[Int](ctxWithGlobal) shouldBe 8
  }

  test("should be able to call generic functions with varArgs") {
    parse[Int]("#processHelper.genericFunctionWithVarArg(4)", ctxWithGlobal).validExpression
      .evaluateSync[Int](ctxWithGlobal) shouldBe 4
    parse[Int]("#processHelper.genericFunctionWithVarArg(4, true)", ctxWithGlobal).validExpression
      .evaluateSync[Int](ctxWithGlobal) shouldBe 5
    parse[Int]("#processHelper.genericFunctionWithVarArg(4, true, false, true)", ctxWithGlobal).validExpression
      .evaluateSync[Int](ctxWithGlobal) shouldBe 6
  }

  test("validate selection/projection on non-list") {
    parse[AnyRef]("{:}.![#this.sthsth]") shouldBe Symbol("invalid")
    parse[AnyRef]("{:}.?[#this.sthsth]") shouldBe Symbol("invalid")
    parse[AnyRef]("''.?[#this.sthsth]") shouldBe Symbol("invalid")
  }

  test("allow selection/projection on maps") {
    parse[java.util.Map[String, Any]]("{a:1}.?[key=='']", ctx).validExpression
      .evaluateSync[java.util.Map[String, Any]]() shouldBe Map().asJava
    parse[java.util.Map[String, Any]]("{a:1}.?[value==1]", ctx).validExpression
      .evaluateSync[java.util.Map[String, Any]]() shouldBe Map("a" -> 1).asJava

    parse[java.util.List[String]]("{a:1}.![key]", ctx).validExpression
      .evaluateSync[java.util.List[String]]() shouldBe List("a").asJava
    parse[java.util.List[Any]]("{a:1}.![value]", ctx).validExpression
      .evaluateSync[java.util.List[Any]]() shouldBe List(1).asJava
  }

  test("allow selection/projection on maps with #this") {
    parse[java.util.Map[String, Any]]("{a:1}.?[#this.key=='']", ctx).validExpression
      .evaluateSync[java.util.Map[String, Any]]() shouldBe Map().asJava
    parse[java.util.Map[String, Any]]("{a:1}.?[#this.value==1]", ctx).validExpression
      .evaluateSync[java.util.Map[String, Any]]() shouldBe Map("a" -> 1).asJava

    parse[java.util.List[String]]("{a:1}.![#this.key]", ctx).validExpression
      .evaluateSync[java.util.List[String]]() shouldBe List("a").asJava
    parse[java.util.List[Any]]("{a:1}.![#this.value]", ctx).validExpression
      .evaluateSync[java.util.List[Any]]() shouldBe List(1).asJava
  }

  test("invokes methods on primitives correctly") {
    def invokeAndCheck[T: TypeTag](expr: String, result: T): Unit = {
      val parsed = parse[T](expr).validExpression
      // Bytecode generation happens only after successful invoke at times. To be sure we're there we round it up to 5 ;)
      (1 to 5).foreach { _ =>
        parsed.evaluateSync[T](ctx) shouldBe result
      }
    }

    invokeAndCheck("1.toString", "1")
    invokeAndCheck("1.toString()", "1")
    invokeAndCheck("1.doubleValue", 1d)
    invokeAndCheck("1.doubleValue()", 1d)

    invokeAndCheck("false.toString", "false")
    invokeAndCheck("false.toString()", "false")

    invokeAndCheck("false.booleanValue", false)
    invokeAndCheck("false.booleanValue()", false)

    // not primitives, just to make sure toString works on other objects...
    invokeAndCheck("{}.toString", "[]")
    invokeAndCheck("#obj.id.toString", "1")
  }

  test("should find and invoke primitive parameters correctly") {
    parse[String]("#processHelper.methodWithPrimitiveParams(1, 2, false)", ctxWithGlobal).validExpression
      .evaluateSync[String](ctxWithGlobal) shouldBe "1 2 false"
  }

  test("should type and evaluate constructor for known types") {
    parse[Double]("new java.math.BigDecimal(\"1.2345\", new java.math.MathContext(2)).doubleValue", ctx).validExpression
      .evaluateSync[Double](ctx) shouldBe 1.2
  }

  test("should not validate constructor of unknown type") {
    parse[Any]("new unknown.className(233)", ctx) shouldBe Symbol("invalid")
  }

  test("should not allow property access on Null") {
    inside(parse[Any]("null.property")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Property access on ${TypedNull.display} is not allowed"
    }
  }

  test("should not allow method invocation on Null") {
    inside(parse[Any]("null.method()")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Method invocation on ${TypedNull.display} is not allowed"
    }
  }

  test("should be able to handle spel type conversions") {
    parse[String]("T(java.text.NumberFormat).getNumberInstance('PL').format(12.34)", ctx).validExpression
      .evaluateSync[String](ctx) shouldBe "12,34"
    parse[Locale]("'PL'", ctx).validExpression.expression.evaluateSync[Locale](ctx) shouldBe
      Locale.forLanguageTag("PL")
    parse[LocalDate]("'2007-12-03'", ctx).validExpression.expression.evaluateSync[Locale](ctx) shouldBe
      LocalDate.parse("2007-12-03")
    parse[ChronoLocalDate]("'2007-12-03'", ctx).validExpression.expression.evaluateSync[Locale](ctx) shouldBe
      LocalDate.parse("2007-12-03")
  }

  test("shouldn't allow invalid spel type conversions") {
    inside(parse[LocalDate]("'qwerty'")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Bad expression type, expected: LocalDate, found: String(qwerty)"
    }

    inside(parse[LocalDate]("''")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Bad expression type, expected: LocalDate, found: String()"
    }

    inside(parse[Currency]("'qwerty'")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Bad expression type, expected: Currency, found: String(qwerty)"
    }

    inside(parse[UUID]("'qwerty'")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Bad expression type, expected: UUID, found: String(qwerty)"
    }

    inside(parse[Locale]("'qwerty'")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Bad expression type, expected: Locale, found: String(qwerty)"
    }

    inside(parse[Charset]("'qwerty'")) { case Invalid(NonEmptyList(error: ExpressionParseError, Nil)) =>
      error.message shouldBe s"Bad expression type, expected: Charset, found: String(qwerty)"
    }
  }

  test("comparison of generic type with not generic type") {
    val result = parseV[Any](
      "#a.someComparable == 2L",
      ValidationContext.empty
        .withVariable("a", Typed.fromInstance(SampleGlobalObject), None)
        .validValue
    )
    result shouldBe Symbol("valid")
  }

  private def checkExpressionWithKnownResult(expr: String): Unit = {
    val parsed   = parse[Any](expr).validValue
    val expected = parsed.expression.evaluateSync[Any](ctx)
    parsed.returnType shouldBe Typed.fromInstance(expected)
  }

  test("should calculate values of operators") {
    def checkOneOperand(op: String, a: Any): Unit =
      checkExpressionWithKnownResult(s"$op$a")

    def checkTwoOperands(op: String, a: Any, b: Any): Unit =
      checkExpressionWithKnownResult(s"$a $op $b")

    val oneOperandOp        = Gen.oneOf("+", "-")
    val twoOperandOp        = Gen.oneOf("+", "-", "*", "==", "!=", ">", ">=", "<", "<=")
    val twoOperandNonZeroOp = Gen.oneOf("/", "%")

    val positiveNumberGen = Gen.oneOf(1, 2, 5, 10, 25)
    val nonZeroNumberGen  = Gen.oneOf(-5, -1, 1, 2, 5, 10, 25)
    val anyNumberGen      = Gen.oneOf(-5, -1, 0, 1, 2, 5, 10, 25)

    ScalaCheckDrivenPropertyChecks.forAll(oneOperandOp, positiveNumberGen)(checkOneOperand)
    ScalaCheckDrivenPropertyChecks.forAll(twoOperandOp, anyNumberGen, anyNumberGen)(checkTwoOperands)
    ScalaCheckDrivenPropertyChecks.forAll(twoOperandNonZeroOp, anyNumberGen, nonZeroNumberGen)(checkTwoOperands)
  }

  test("should calculate values of operators on strings") {
    checkExpressionWithKnownResult("'a' + 1")
    checkExpressionWithKnownResult("1 + 'a'")
    checkExpressionWithKnownResult("'a' + 'a'")
  }

  test("should not validate division by zero") {
    parse[Any]("1 / 0").invalidValue shouldBe NonEmptyList.one(DivisionByZeroError("(1 / 0)"))
    parse[Any]("1 % 0").invalidValue shouldBe NonEmptyList.one(ModuloZeroError("(1 % 0)"))
  }

  test("should check map values") {
    val parser = expressionParser()
    val expected = Typed.genericTypeClass[java.util.Map[_, _]](
      List(Typed[String], Typed.record(Map("additional" -> Typed[String])))
    )
    inside(parser.parse("""{"aField": {"additional": 1}}""", ValidationContext.empty, expected)) {
      case Invalid(NonEmptyList(e: ExpressionTypeError, Nil)) =>
        e.expected shouldBe expected
    }
    parser.parse("""{"aField": {"additional": "str"}}""", ValidationContext.empty, expected) shouldBe Symbol("valid")
  }

  test("should use generic parameters in method return types") {
    forAll(
      Table(
        ("expression", "expectedResultType"),
        ("{foo: 1, bar: 2}.get('foo')", Typed[Int]),
        ("{foo: 1, bar: 'string'}.get('foo')", Unknown),
        ("{foo: 1, bar: 2}.getOrDefault('foo', 1)", Typed[Int]),
        ("{foo: 1, bar: 2}.values", Typed.fromDetailedType[java.util.Collection[Integer]]),
        ("{foo: 1, bar: 2}.keySet", Typed.fromDetailedType[java.util.Set[String]]),
        ("{1, 2}.get(0)", Typed[Int]),
        ("#optional.get", Typed[Int]),
        ("#optional.orElse(123)", Typed[Int]),
      )
    ) { (expression, expectedResultType) =>
      val validationContext = ValidationContext.empty
        .withVariable("optional", Typed.fromDetailedType[Optional[Integer]], None)
        .validValue
      parseV[Any](expression, validationContext).validValue.typingInfo.typingResult shouldBe expectedResultType
    }
  }

  test("should not allow array constructor") {
    List("new String[]", "new String[ ]", "new String[0]", "new String[#invalidRef]", "new String[invalidSyntax]").map(
      illegalExpr => parse[Any](illegalExpr, ctx).invalidValue shouldBe NonEmptyList.one(ArrayConstructorError)
    )
  }

  test("indexing on maps and lists should validate expression inside indexer") {
    List("#processHelper.stringOnStringMap[#invalidRef]", "{1,2,3}[#invalidRef]").map(expr =>
      parse[Any](expr, ctxWithGlobal).invalidValue shouldBe NonEmptyList.one(
        UnresolvedReferenceError("invalidRef")
      )
    )
  }

  test("indexing on unknown should validate expression inside indexer") {
    parse[Any]("#unknownString.value[#invalidRef]", ctx).invalidValue shouldBe NonEmptyList.one(
      UnresolvedReferenceError("invalidRef")
    )
  }

  test("indexing on class should validate expression inside indexer") {
    parse[Any](
      "T(java.time.LocalDate)[#invalidRef]",
      ctx,
      dynamicPropertyAccessAllowed = true
    ).invalidValue shouldBe NonEmptyList.one(
      UnresolvedReferenceError("invalidRef")
    )
  }

  test("should return correct type in array projection") {
    evaluate[Any]("#array.![#this]") shouldBe Array("a", "b")
  }

  test("should return error on String projection") {
    parse[Any]("'ab'.![#this]", ctx).invalidValue.toList.headOption.value shouldBe a[IllegalProjectionSelectionError]
  }

  test("should convert array to list when passing arg which type should be list") {
    evaluate[Any]("T(java.lang.String).join(',', #array)") shouldBe "a,b"
  }

  test("should calculate correct type of list after projection on list") {
    val parsed = parse[Any]("{1, 2, 3}.![{a: #this}]", ctx).validValue
    parsed.returnType shouldBe Typed.genericTypeClass[java.util.List[_]](
      List(
        Typed.record(
          List("a" -> Typed.typedClass[Integer])
        )
      )
    )
  }

  test("should calculate correct type of list after projection on map") {
    val parsed = parse[Any]("{a: 100}.![#this]", ctx).validValue
    parsed.returnType shouldBe Typed.genericTypeClass[java.util.List[_]](
      List(
        Typed.record(
          List(
            "key"   -> Typed.typedClass[String],
            "value" -> TypedObjectWithValue(Typed.typedClass[Integer], 100)
          )
        )
      )
    )
  }

  test("should allow using list methods on array projection") {
    evaluate[Any]("'a,b'.split(',').![#this].isEmpty()") shouldBe false
    evaluate[String]("'a,b'.split(',').![#this].get(0)") shouldBe "a"
  }

  test("should allow using list methods on array") {
    evaluate[Any]("#array.isEmpty()") shouldBe false
    evaluate[Any]("#intArray.isEmpty()") shouldBe false
    evaluate[String]("#array.get(0)") shouldBe "a"
  }

  test("should allow using list methods on nested arrays") {
    evaluate[Any]("#nestedArray.![#this.isEmpty()]") shouldBe Array(false, false)
  }

  test("should check if method exists as a fallback of not found property") {
    parse[Any]("{1, 2}.contains", ctx).invalidValue.toList should matchPattern {
      case ArgumentTypeError("contains", _, _) :: Nil =>
    }
    parse[Any]("{1, 2}.contain", ctx).invalidValue.toList should matchPattern {
      case NoPropertyError(_, "contain") :: Nil =>
    }
  }

  test("should check if a type can be casted to a given type") {
    forAll(
      Table(
        ("expression", "expectedResult"),
        ("#unknownString.value.canBe('java.lang.String')", true),
        ("#unknownString.value.canBe('java.lang.Integer')", false),
      )
    ) { (expression, expectedResult) =>
      evaluate[Any](expression) shouldBe expectedResult
    }
  }

  test("should compute correct result type based on parameter") {
    val parsed = parse[Any]("#unknownString.value.to('java.lang.String')", ctx).validValue
    parsed.returnType shouldBe Typed.typedClass[String]
    parsed.expression.evaluateSync[Any](ctx) shouldBe a[java.lang.String]
  }

  test("should return an error if the cast return type cannot be determined at parse time") {
    parse[Any]("#unknownString.value.to('java.util.XYZ')", ctx).invalidValue.toList should matchPattern {
      case GenericFunctionError("Cannot cast or convert to: 'java.util.XYZ'") :: Nil =>
    }
    parse[Any]("#unknownString.value.to(#obj.id)", ctx).invalidValue.toList should matchPattern {
      case ArgumentTypeError("to", _, _) :: Nil =>
    }
  }

  test("should throw exception if cast fails") {
    val caught = intercept[SpelExpressionEvaluationException] {
      evaluate[Any]("#unknownString.value.to('java.lang.Integer')")
    }
    caught.getMessage should include("Cannot cast or convert value: unknown to: 'java.lang.Integer'")
    caught.getCause shouldBe a[IllegalStateException]
  }

  test("should not allow cast to disallowed classes") {
    parse[Any](
      "#hashMap.value.to('java.util.HashMap').remove('testKey')",
      ctx.withVariable("hashMap", ContainerOfUnknown(new java.util.HashMap[String, Int](Map("testKey" -> 2).asJava)))
    ).invalidValue.toList should matchPattern {
      case GenericFunctionError("Cannot cast or convert to: 'java.util.HashMap'") :: IllegalInvocationError(
            Unknown
          ) :: Nil =>
    }
  }

  test(
    "should allow invoke discovered methods for unknown objects - not matter how methodExecutionForUnknownAllowed is set"
  ) {
    def typedArray(elementTypingResult: TypingResult): TypingResult =
      Typed.genericTypeClass(classOf[Array[Object]], List(elementTypingResult))
    forAll(
      Table(
        ("expression", "expectedType", "expectedResult", "methodExecutionForUnknownAllowed"),
        ("#arrayOfUnknown", typedArray(Unknown), Array("unknown"), false),
        ("#arrayOfUnknown.![#this.toString()]", typedArray(Typed.typedClass[String]), Array("unknown"), false),
        ("#arrayOfUnknown.![#this.toString()]", typedArray(Typed.typedClass[String]), Array("unknown"), true),
      )
    ) { (expression, expectedType, expectedResult, methodExecutionForUnknownAllowed) =>
      val parsed = parse[Any](
        expr = expression,
        context = ctx,
        methodExecutionForUnknownAllowed = methodExecutionForUnknownAllowed
      ).validValue
      parsed.returnType shouldBe expectedType
      parsed.expression.evaluateSync[Any](ctx) shouldBe expectedResult
    }
  }

  test(
    "should allow invoke undiscovered methods for unknown objects when flag methodExecutionForUnknownAllowed is set to true"
  ) {
    parse[Any](
      expr = "#arrayOfUnknown.![#this.indexOf('n')]",
      context = ctx,
      methodExecutionForUnknownAllowed = true
    ).validExpression.evaluateSync[Any](ctx) shouldBe Array(1)
  }

  test(
    "should not allow invoke undiscovered methods for unknown objects when flag methodExecutionForUnknownAllowed is set to false"
  ) {
    parse[Any](
      expr = "#arrayOfUnknown.![#this.indexOf('n')]",
      context = ctx,
      methodExecutionForUnknownAllowed = false
    ).invalidValue.toList should matchPattern { case IllegalInvocationError(Unknown) :: Nil =>
    }
  }

  test("should return null if toOrNull fails") {
    evaluate[Any]("#unknownString.value.toOrNull('java.lang.Long')") == null shouldBe true
  }

  test("should toOrNull succeed") {
    evaluate[Any](
      "#unknownLong.value.toOrNull('java.lang.Long')",
      ctx.withVariable("unknownLong", ContainerOfUnknown(11L))
    ) shouldBe 11L
  }

  test("should allow invoke conversion methods with class simple names") {
    evaluate[Any](
      "{#unknownLong.value.toOrNull('Long'), #unknownLong.value.to('Long'), #unknownLong.value.canBe('Long')}",
      ctx.withVariable("unknownLong", ContainerOfUnknown(11L))
    ) shouldBe List(11L, 11L, true).asJava
  }

  test("should convert a List to a Map") {
    val mapStringStringType =
      Typed.genericTypeClass[JMap[_, _]](List(Typed.typedClass[String], Typed.typedClass[String]))
    val mapStringUnknownType =
      Typed.genericTypeClass[JMap[_, _]](List(Typed.typedClass[String], Unknown))
    val stringMap = Map("foo" -> "bar", "baz" -> "qux").asJava
    val nullableMap = {
      val result = new util.HashMap[String, String]()
      result.put("foo", "bar")
      result.put("baz", null)
      result.put(null, "qux")
      result
    }
    val mapWithDifferentValueTypes = Map("foo" -> "bar", "baz" -> 1).asJava
    val mapWithKeyAndValueFields   = Map("key" -> "foo", "value" -> 123).asJava
    val customCtx = ctx
      .withVariable("stringMap", stringMap)
      .withVariable("mapWithDifferentValueTypes", mapWithDifferentValueTypes)
      .withVariable("nullableMap", nullableMap)
      .withVariable("containerWithMapWithKeyAndValueFields", ContainerOfGenericMap(mapWithKeyAndValueFields))

    forAll(
      Table(
        ("expression", "expectedType", "expectedResult"),
        (
          "#stringMap.![{key: #this.key + '_k', value: #this.value + '_v'}].toMap",
          mapStringStringType,
          Map("foo_k" -> "bar_v", "baz_k" -> "qux_v").asJava
        ),
        (
          "#mapWithDifferentValueTypes.![{key: #this.key, value: #this.value}].toMap",
          mapStringUnknownType,
          mapWithDifferentValueTypes
        ),
        (
          "#nullableMap.![{key: #this.key, value: #this.value}].toMap",
          mapStringStringType,
          nullableMap
        ),
        (
          "{}.toMap",
          Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown)),
          Map.empty.asJava,
        ),
        (
          "{#containerWithMapWithKeyAndValueFields.value}.toMap",
          Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown)),
          Map("foo" -> 123).asJava,
        )
      )
    ) { (expression, expectedType, expectedResult) =>
      val parsed = parse[Any](expr = expression, context = customCtx).validValue
      parsed.returnType.withoutValue shouldBe expectedType
      parsed.expression.evaluateSync[Any](customCtx) shouldBe expectedResult
    }
  }

  test("should convert list of map entries into map") {
    val result = evaluate[Any]("""
        |{
        |a: "A",
        |b: "B"
        |}.![#this].toMap
        |""".stripMargin)
    result shouldBe java.util.Map.of("a", "A", "b", "B")
  }

  test("should be able to convert list of map entries into map") {
    val result = evaluate[Any]("""
                                 |{
                                 |a: "A",
                                 |b: "B"
                                 |}.![#this].canBeMap
                                 |""".stripMargin)
    result shouldBe true
  }

  test("should be able to convert list of map entries into map or null") {
    val result = evaluate[Any]("""
                                 |{
                                 |a: "A",
                                 |b: "B"
                                 |}.![#this].toMapOrNull
                                 |""".stripMargin)
    result shouldBe java.util.Map.of("a", "A", "b", "B")
  }

  test("should return error msg if record in map project does not contain required fields") {
    parse[Any]("#mapValue.![{invalid_key: #this.key}].toMap()", ctx).invalidValue.toList should matchPattern {
      case GenericFunctionError("List element must contain 'key' and 'value' fields") :: Nil =>
    }
  }

  test("should type conversion of list of unknown to map correctly and return error in runtime") {
    val parsed = parse[Any]("{1, 'foo', false}.toMap", ctx).validValue
    parsed.returnType.withoutValue shouldBe Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown))
    an[SpelExpressionEvaluationException] shouldBe thrownBy {
      parsed.expression.evaluateSync[Any](ctx)
    }
  }

  test("should convert value to given type with correct return type") {
    evaluate[Int]("'1'.to('Integer')") shouldBe 1
    evaluate[Int]("'1'.toOrNull('Integer')") shouldBe 1
    evaluate[JBoolean]("'1'.canBe('Integer')") shouldBe true
    evaluate[Long]("'1'.toLong") shouldBe 1
    evaluate[Long]("'1'.toLongOrNull") shouldBe 1
    evaluate[JBoolean]("'1'.canBeLong") shouldBe true
  }

  test("should be able to run indexer on created with toMap map") {
    evaluate[Int]("{{key: 1, value: 5}}.toMap[1]") shouldBe 5
  }

  test("should be able to run indexer on created with toMapOrNull map") {
    evaluate[Int]("{{key: 1, value: 5}}.toMapOrNull[1]") shouldBe 5
  }

  test("should convert value to a given type") {
    val map                         = Map("a" -> "b").asJava
    val emptyMap                    = Map().asJava
    val list                        = List("a").asJava
    val listOfTuples                = List(Map("key" -> "a", "value" -> "b").asJava).asJava
    val emptyList                   = List().asJava
    val emptyTuplesList             = List(Map().asJava).asJava
    val convertedDoubleToBigDecimal = NumberUtils.convertNumberToTargetClass(1.1, classOf[JBigDecimal])
    val zoneOffset                  = ZoneOffset.of("+01:00")
    val zoneId                      = ZoneId.of("Europe/Warsaw")
    val locale                      = StringUtils.parseLocale("pl_PL")
    val charset                     = StandardCharsets.UTF_8
    val currency                    = Currency.getInstance("USD")
    val uuid                        = UUID.fromString("7447e433-83dd-47d0-a115-769a03236bca")
    val localTime                   = LocalTime.parse("10:15:30")
    val localDate                   = LocalDate.parse("2024-11-04")
    val localDateTime               = LocalDateTime.parse("2024-11-04T10:15:30")
    val customCtx = ctx
      .withVariable("unknownInteger", ContainerOfUnknown(1))
      .withVariable("unknownBoolean", ContainerOfUnknown(false))
      .withVariable("unknownBooleanString", ContainerOfUnknown("false"))
      .withVariable("unknownLong", ContainerOfUnknown(11L))
      .withVariable("unknownLongString", ContainerOfUnknown("11"))
      .withVariable("unknownDouble", ContainerOfUnknown(1.1))
      .withVariable("unknownDoubleString", ContainerOfUnknown("1.1"))
      .withVariable("unknownBigDecimal", ContainerOfUnknown(BigDecimal(2.1).bigDecimal))
      .withVariable("unknownBigDecimalString", ContainerOfUnknown("2.1"))
      .withVariable("unknownMap", ContainerOfUnknown(map))
      .withVariable("unknownList", ContainerOfUnknown(list))
      .withVariable("unknownListOfTuples", ContainerOfUnknown(listOfTuples))
      .withVariable("unknownEmptyList", ContainerOfUnknown(emptyList))
      .withVariable("unknownEmptyTuplesList", ContainerOfUnknown(emptyTuplesList))
    val byteTyping                = Typed.typedClass[JByte]
    val shortTyping               = Typed.typedClass[JShort]
    val integerTyping             = Typed.typedClass[JInteger]
    val longTyping                = Typed.typedClass[JLong]
    val floatTyping               = Typed.typedClass[JFloat]
    val doubleTyping              = Typed.typedClass[JDouble]
    val bigDecimalTyping          = Typed.typedClass[JBigDecimal]
    val bigIntegerTyping          = Typed.typedClass[JBigInteger]
    val booleanTyping             = Typed.typedClass[JBoolean]
    val stringTyping              = Typed.typedClass[String]
    val mapTyping                 = Typed.genericTypeClass[JMap[_, _]](List(Unknown, Unknown))
    val listTyping                = Typed.genericTypeClass[JList[_]](List(Unknown))
    val zoneOffsetTyping          = Typed.typedClass[ZoneOffset]
    val zoneIdTyping              = Typed.typedClass[ZoneId]
    val localeTyping              = Typed.typedClass[Locale]
    val charsetTyping             = Typed.typedClass[Charset]
    val currencyTyping            = Typed.typedClass[Currency]
    val uuidTyping                = Typed.typedClass[UUID]
    val localTimeTyping           = Typed.typedClass[LocalTime]
    val localDateTyping           = Typed.typedClass[LocalDate]
    val localDateTimeTyping       = Typed.typedClass[LocalDateTime]
    val chronoLocalDateTyping     = Typed.typedClass[ChronoLocalDate]
    val chronoLocalDateTimeTyping = Typed.typedClass[ChronoLocalDateTime[_]]
    forAll(
      Table(
        ("expression", "expectedType", "expectedResult"),
        ("1.to('Long')", longTyping, 1),
        ("1.1.to('Long')", longTyping, 1),
        ("'1'.to('Long')", longTyping, 1),
        ("1.toOrNull('Long')", longTyping, 1),
        ("1.1.toOrNull('Long')", longTyping, 1),
        ("'1'.toOrNull('Long')", longTyping, 1),
        ("'a'.toOrNull('Long')", longTyping, null),
        ("#unknownLong.value.to('Long')", longTyping, 11),
        ("#unknownLongString.value.to('Long')", longTyping, 11),
        ("#unknownDouble.value.to('Long')", longTyping, 1),
        ("#unknownDoubleString.value.to('Long')", longTyping, 1),
        ("#unknownBoolean.value.toOrNull('Long')", longTyping, null),
        ("1.to('Double')", doubleTyping, 1.0),
        ("1.1.to('Double')", doubleTyping, 1.1),
        ("'1'.to('Double')", doubleTyping, 1.0),
        ("1.toOrNull('Double')", doubleTyping, 1.0),
        ("1.1.toOrNull('Double')", doubleTyping, 1.1),
        ("'1'.toOrNull('Double')", doubleTyping, 1.0),
        ("'a'.toOrNull('Double')", doubleTyping, null),
        ("#unknownLong.value.to('Double')", doubleTyping, 11.0),
        ("#unknownLongString.value.to('Double')", doubleTyping, 11.0),
        ("#unknownDouble.value.to('Double')", doubleTyping, 1.1),
        ("#unknownDoubleString.value.to('Double')", doubleTyping, 1.1),
        ("#unknownBoolean.value.toOrNull('Double')", doubleTyping, null),
        (
          "1.to('BigDecimal')",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        ("1.1.to('BigDecimal')", bigDecimalTyping, convertedDoubleToBigDecimal),
        (
          "'1'.to('BigDecimal')",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        (
          "1.toBigDecimalOrNull()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        ("1.1.toOrNull('BigDecimal')", bigDecimalTyping, convertedDoubleToBigDecimal),
        (
          "'1'.toOrNull('BigDecimal')",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        ("'a'.toOrNull('BigDecimal')", bigDecimalTyping, null),
        (
          "#unknownLong.value.to('BigDecimal')",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(11).bigDecimal)
        ),
        (
          "#unknownLongString.value.to('BigDecimal')",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(11).bigDecimal)
        ),
        ("#unknownDouble.value.to('BigDecimal')", bigDecimalTyping, convertedDoubleToBigDecimal),
        (
          "#unknownDoubleString.value.to('BigDecimal')",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1.1).bigDecimal)
        ),
        ("#unknownBoolean.value.toOrNull('BigDecimal')", bigDecimalTyping, null),
        ("'true'.to('Boolean')", booleanTyping, true),
        ("#unknownInteger.value.toOrNull('Boolean')", booleanTyping, null),
        ("'a'.toOrNull('Boolean')", booleanTyping, null),
        ("'true'.toOrNull('Boolean')", booleanTyping, true),
        ("#unknownBoolean.value.to('Boolean')", booleanTyping, false),
        ("'a'.to('String')", stringTyping, "a"),
        ("'a'.toOrNull('String')", stringTyping, "a"),
        ("1.to('Byte')", byteTyping, 1.byteValue),
        ("'1'.to('Byte')", byteTyping, 1.byteValue),
        ("1.toOrNull('Byte')", byteTyping, 1.byteValue),
        ("'1'.toOrNull('Byte')", byteTyping, 1.byteValue),
        ("1.to('Short')", shortTyping, 1.shortValue),
        ("'1'.to('Short')", shortTyping, 1.shortValue),
        ("1.toOrNull('Short')", shortTyping, 1.shortValue),
        ("'1'.toOrNull('Short')", shortTyping, 1.shortValue),
        ("1.to('Integer')", integerTyping, 1),
        ("'1'.to('Integer')", integerTyping, 1),
        ("1.toOrNull('Integer')", integerTyping, 1),
        ("'1'.toOrNull('Integer')", integerTyping, 1),
        ("1.1.to('Float')", floatTyping, 1.1.floatValue),
        ("'1.1'.to('Float')", floatTyping, 1.1.floatValue),
        ("1.1.toOrNull('Float')", floatTyping, 1.1.floatValue),
        ("'1.1'.toOrNull('Float')", floatTyping, 1.1.floatValue),
        ("1.to('BigInteger')", bigIntegerTyping, JBigInteger.ONE),
        ("'1'.to('BigInteger')", bigIntegerTyping, JBigInteger.ONE),
        ("1.toOrNull('BigInteger')", bigIntegerTyping, JBigInteger.ONE),
        ("'1'.toOrNull('BigInteger')", bigIntegerTyping, JBigInteger.ONE),
        ("'+01:00'.to('ZoneOffset')", zoneOffsetTyping, zoneOffset),
        ("'Europe/Warsaw'.to('ZoneId')", zoneIdTyping, zoneId),
        ("'pl_PL'.to('Locale')", localeTyping, locale),
        ("'UTF-8'.to('Charset')", charsetTyping, charset),
        ("'USD'.to('Currency')", currencyTyping, currency),
        ("'7447e433-83dd-47d0-a115-769a03236bca'.to('UUID')", uuidTyping, uuid),
        ("'10:15:30'.to('LocalTime')", localTimeTyping, localTime),
        ("'2024-11-04'.to('LocalDate')", localDateTyping, localDate),
        ("'2024-11-04T10:15:30'.to('LocalDateTime')", localDateTimeTyping, localDateTime),
        ("'2024-11-04'.to('ChronoLocalDate')", chronoLocalDateTyping, localDate),
        ("'2024-11-04T10:15:30'.to('ChronoLocalDateTime')", chronoLocalDateTimeTyping, localDateTime),
        ("#unknownString.value.to('String')", stringTyping, "unknown"),
        ("#unknownMap.value.to('Map')", mapTyping, map),
        ("#unknownMap.value.toOrNull('Map')", mapTyping, map),
        ("#unknownList.value.to('List')", listTyping, list),
        ("#unknownList.value.toOrNull('List')", listTyping, list),
        ("#unknownListOfTuples.value.to('Map')", mapTyping, map),
        ("#unknownListOfTuples.value.toOrNull('Map')", mapTyping, map),
        ("#unknownEmptyList.value.to('Map')", mapTyping, emptyMap),
        ("#unknownEmptyList.value.to('List')", listTyping, emptyList),
        ("#unknownString.value.toOrNull('Map')", mapTyping, null),
        ("#unknownEmptyTuplesList.value.toOrNull('Map')", mapTyping, null),
        ("#unknownEmptyTuplesList.value.to('List')", listTyping, emptyTuplesList),
        ("#unknownString.value.toOrNull('List')", listTyping, null),
        ("1.toLong()", longTyping, 1),
        ("1.1.toLong()", longTyping, 1),
        ("'1'.toLong()", longTyping, 1),
        ("1.toLongOrNull()", longTyping, 1),
        ("1.1.toLongOrNull()", longTyping, 1),
        ("'1'.toLongOrNull()", longTyping, 1),
        ("'a'.toLongOrNull()", longTyping, null),
        ("#unknownLong.value.toLong()", longTyping, 11),
        ("#unknownLongString.value.toLong()", longTyping, 11),
        ("#unknownDouble.value.toLong()", longTyping, 1),
        ("#unknownDoubleString.value.toLong()", longTyping, 1),
        ("#unknownBoolean.value.toLongOrNull()", longTyping, null),
        ("1L.toInteger()", integerTyping, 1),
        ("1.1.toInteger()", integerTyping, 1),
        ("'1'.toInteger()", integerTyping, 1),
        ("1L.toIntegerOrNull()", integerTyping, 1),
        ("1.1.toIntegerOrNull()", integerTyping, 1),
        ("'1'.toIntegerOrNull()", integerTyping, 1),
        ("'a'.toIntegerOrNull()", integerTyping, null),
        ("#unknownLong.value.toInteger()", integerTyping, 11),
        ("#unknownLongString.value.toInteger()", integerTyping, 11),
        ("#unknownDouble.value.toInteger()", integerTyping, 1),
        ("#unknownDoubleString.value.toInteger()", integerTyping, 1),
        ("#unknownBoolean.value.toIntegerOrNull()", integerTyping, null),
        ("1.toDouble()", doubleTyping, 1.0),
        ("'1'.toDouble()", doubleTyping, 1.0),
        ("1.toDoubleOrNull()", doubleTyping, 1.0),
        ("'1'.toDoubleOrNull()", doubleTyping, 1.0),
        ("'a'.toDoubleOrNull()", doubleTyping, null),
        ("#unknownLong.value.toDouble()", doubleTyping, 11.0),
        ("#unknownLongString.value.toDouble()", doubleTyping, 11.0),
        ("#unknownDouble.value.toDouble()", doubleTyping, 1.1),
        ("#unknownDoubleString.value.toDouble()", doubleTyping, 1.1),
        ("#unknownBoolean.value.toDoubleOrNull()", doubleTyping, null),
        ("1.toBigDecimal()", bigDecimalTyping, BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)),
        ("1.1.toBigDecimal()", bigDecimalTyping, convertedDoubleToBigDecimal),
        (
          "'1'.toBigDecimal()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        (
          "1.toBigDecimalOrNull()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        ("1.1.toBigDecimalOrNull()", bigDecimalTyping, convertedDoubleToBigDecimal),
        (
          "'1'.toBigDecimalOrNull()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1).bigDecimal)
        ),
        ("'a'.toBigDecimalOrNull()", bigDecimalTyping, null),
        (
          "#unknownLong.value.toBigDecimal()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(11).bigDecimal)
        ),
        (
          "#unknownLongString.value.toBigDecimal()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(11).bigDecimal)
        ),
        ("#unknownDouble.value.toBigDecimal()", bigDecimalTyping, convertedDoubleToBigDecimal),
        (
          "#unknownDoubleString.value.toBigDecimal()",
          bigDecimalTyping,
          BigDecimalScaleEnsurer.ensureBigDecimalScale(BigDecimal(1.1).bigDecimal)
        ),
        ("#unknownBoolean.value.toBigDecimalOrNull()", bigDecimalTyping, null),
        ("'true'.toBoolean()", booleanTyping, true),
        ("#unknownInteger.value.toBooleanOrNull()", booleanTyping, null),
        ("'a'.toBooleanOrNull()", booleanTyping, null),
        ("'true'.toBooleanOrNull()", booleanTyping, true),
        ("#unknownBoolean.value.toBoolean()", booleanTyping, false),
        ("#unknownMap.value.toMap()", mapTyping, map),
        ("#unknownMap.value.toMapOrNull()", mapTyping, map),
        ("#unknownList.value.toList()", listTyping, list),
        ("#unknownList.value.toListOrNull()", listTyping, list),
        ("#unknownListOfTuples.value.toMap()", mapTyping, map),
        ("#unknownListOfTuples.value.toMapOrNull()", mapTyping, map),
        ("#unknownEmptyList.value.toMap()", mapTyping, emptyMap),
        ("#unknownEmptyList.value.toList()", listTyping, emptyList),
        ("#unknownString.value.toMapOrNull()", mapTyping, null),
        ("#unknownEmptyTuplesList.value.toMapOrNull()", mapTyping, null),
        ("#unknownEmptyTuplesList.value.toList()", listTyping, emptyTuplesList),
        ("#unknownString.value.toListOrNull()", listTyping, null),
        ("#arrayOfUnknown.toList()", listTyping, List("unknown").asJava),
      )
    ) { (expression, expectedType, expectedResult) =>
      val parsed = parse[Any](expr = expression, context = customCtx).validValue
      parsed.returnType.withoutValue shouldBe expectedType
      parsed.expression.evaluateSync[Any](customCtx) shouldBe expectedResult
    }
  }

  test("should check if unknown can be converted to a given type") {
    val map        = Map("a" -> "b").asJava
    val list       = List("a").asJava
    val tuplesList = List(Map("key" -> "a", "value" -> "b").asJava).asJava
    val customCtx = ctx
      .withVariable("unknownBoolean", ContainerOfUnknown(true))
      .withVariable("unknownBooleanString", ContainerOfUnknown("false"))
      .withVariable("unknownLong", ContainerOfUnknown(11L))
      .withVariable("unknownInteger", ContainerOfUnknown(1))
      .withVariable("unknownIntegerString", ContainerOfUnknown("1"))
      .withVariable("unknownLongString", ContainerOfUnknown("11"))
      .withVariable("unknownDouble", ContainerOfUnknown(1.1))
      .withVariable("unknownDoubleString", ContainerOfUnknown("1.1"))
      .withVariable("unknownBigDecimal", ContainerOfUnknown(BigDecimal(2.1).bigDecimal))
      .withVariable("unknownBigDecimalString", ContainerOfUnknown("2.1"))
      .withVariable("unknownList", ContainerOfUnknown(list))
      .withVariable("unknownListOfTuples", ContainerOfUnknown(tuplesList))
      .withVariable("unknownMap", ContainerOfUnknown(map))
    forAll(
      Table(
        ("expression", "result"),
        ("#unknownBoolean.value.canBe('Boolean')", true),
        ("#unknownBooleanString.value.canBe('Boolean')", true),
        ("#unknownString.value.canBe('Boolean')", false),
        ("#unknownLong.value.canBe('Byte')", true),
        ("#unknownLongString.value.canBe('Byte')", true),
        ("#unknownString.value.canBe('Byte')", false),
        ("#unknownLong.value.canBe('Short')", true),
        ("#unknownLongString.value.canBe('Short')", true),
        ("#unknownString.value.canBe('Short')", false),
        ("#unknownLong.value.canBe('Integer')", true),
        ("#unknownLongString.value.canBe('Integer')", true),
        ("#unknownString.value.canBe('Integer')", false),
        ("#unknownLong.value.canBe('Long')", true),
        ("#unknownLongString.value.canBe('Long')", true),
        ("#unknownString.value.canBe('Long')", false),
        ("#unknownLong.value.canBe('BigInteger')", true),
        ("#unknownLongString.value.canBe('BigInteger')", true),
        ("#unknownString.value.canBe('BigInteger')", false),
        ("#unknownDouble.value.canBe('Double')", true),
        ("#unknownDoubleString.value.canBe('Double')", true),
        ("#unknownString.value.canBe('Double')", false),
        ("#unknownDouble.value.canBe('Float')", true),
        ("#unknownDoubleString.value.canBe('Float')", true),
        ("#unknownString.value.canBe('Float')", false),
        ("#unknownBigDecimal.value.canBe('BigDecimal')", true),
        ("#unknownBigDecimalString.value.canBe('BigDecimal')", true),
        ("#unknownString.value.canBe('BigDecimal')", false),
        ("#unknownList.value.canBe('List')", true),
        ("#unknownList.value.canBe('Map')", false),
        ("#unknownMap.value.canBe('List')", true),
        ("#unknownMap.value.canBe('Map')", true),
        ("#unknownListOfTuples.value.canBe('List')", true),
        ("#unknownListOfTuples.value.canBe('Map')", true),
        ("#unknownBoolean.value.canBeBoolean", true),
        ("#unknownBooleanString.value.canBeBoolean", true),
        ("#unknownString.value.canBeBoolean", false),
        ("#unknownLong.value.canBeLong", true),
        ("#unknownLongString.value.canBeLong", true),
        ("#unknownString.value.canBeLong", false),
        ("#unknownInteger.value.canBeInteger", true),
        ("#unknownIntegerString.value.canBeInteger", true),
        ("#unknownString.value.canBeInteger", false),
        ("#unknownDouble.value.canBeDouble", true),
        ("#unknownDoubleString.value.canBeDouble", true),
        ("#unknownString.value.canBeDouble", false),
        ("#unknownBigDecimal.value.canBeBigDecimal", true),
        ("#unknownBigDecimalString.value.canBeBigDecimal", true),
        ("#unknownString.value.canBeBigDecimal", false),
        ("#unknownList.value.canBeList", true),
        ("#unknownList.value.canBeMap", false),
        ("#unknownMap.value.canBeList", true),
        ("#unknownMap.value.canBeMap", true),
        ("#unknownListOfTuples.value.canBeList", true),
        ("#unknownListOfTuples.value.canBeMap", true),
        ("#arrayOfUnknown.canBeList", true),
      )
    ) { (expression, result) =>
      evaluate[Any](expression, customCtx) shouldBe result
    }
  }

  test("should throw exception if a value cannot be converted to primitive") {
    val customCtx = ctx
      .withVariable("unknownBoolean", ContainerOfUnknown(true))
      .withVariable("unknownLong", ContainerOfUnknown(11L))
      .withVariable("unknownDouble", ContainerOfUnknown(1.1))
      .withVariable("unknownBigDecimal", ContainerOfUnknown(BigDecimal(2.1).bigDecimal))
    Table(
      "expression",
      "#unknownDouble.value.toBoolean()",
      "#unknownLong.value.toBoolean()",
      "#unknownString.value.toBoolean()",
      "#unknownString.value.toLong()",
      "#unknownBoolean.value.toLong()",
      "#unknownString.value.toInteger()",
      "#unknownBoolean.value.toInteger()",
      "#unknownString.value.toDouble()",
      "#unknownBoolean.value.toDouble()",
      "#unknownBoolean.value.toBigDecimal()",
      "#unknownString.value.toBigDecimal()",
      "#unknownString.value.toList()",
      "#unknownString.value.toMap()",
    ).forEvery { expression =>
      val caught = intercept[SpelExpressionEvaluationException] {
        evaluate[Any](expression, customCtx)
      }
      caught.getCause.getCause.getMessage should (
        include("Cannot convert:") or
          include("is neither a decimal digit number")
      )
    }
  }

  test("should convert a set to a list") {
    val parsed = parse[Any]("#setVal.toList()", ctx).validValue
    parsed.returnType shouldBe Typed.genericTypeClass[JList[_]](List(Typed.typedClass[String]))
    parsed.evaluateSync[Any](ctx) shouldBe List("a").asJava
  }

  test("should convert a map to a list analogical as list can be converted to map") {
    val listClass = classOf[JList[_]]

    val containerWithMapWithIntValues            = Map("foo" -> 123, "bar" -> 234).asJava
    val containerWithMapWithDifferentTypesValues = Map("foo" -> 123, "bar" -> "baz").asJava
    val customCtx = Context("someContextId")
      .withVariable("containerWithMapWithIntValues", ContainerOfGenericMap(containerWithMapWithIntValues))
      .withVariable(
        "containerWithMapWithDifferentTypesValues",
        ContainerOfGenericMap(containerWithMapWithDifferentTypesValues)
      )

    forAll(
      Table(
        ("mapExpression", "expectedKeyType", "expectedValueType", "expectedToListResult"),
        ("{:}", Typed[String], Unknown, List.empty.asJava),
        ("{foo: 123}", Typed[String], Typed[Int], List(Map("key" -> "foo", "value" -> 123).asJava).asJava),
        (
          "#containerWithMapWithIntValues.value",
          Unknown,
          Unknown,
          List(
            Map("key" -> "foo", "value" -> 123).asJava,
            Map("key" -> "bar", "value" -> 234).asJava,
          ).asJava
        ),
        (
          "#containerWithMapWithDifferentTypesValues.value",
          Unknown,
          Unknown,
          List(
            Map("key" -> "foo", "value" -> 123).asJava,
            Map("key" -> "bar", "value" -> "baz").asJava,
          ).asJava
        ),
      )
    ) { (mapExpression, expectedKeyType, expectedValueType, expectedToListResult) =>
      val givenMapExpression = parse[Any](mapExpression, customCtx).validValue
      val givenMap           = givenMapExpression.evaluateSync[Any](customCtx)

      val parsedToListExpression = parse[Any](mapExpression + ".toList", customCtx).validValue
      inside(parsedToListExpression.returnType) {
        case TypedClass(`listClass`, (entryType: TypedObjectTypingResult) :: Nil) =>
          entryType.runtimeObjType.klass shouldBe classOf[JMap[_, _]]
          entryType.fields.keySet shouldBe Set("key", "value")
          entryType.fields("key") shouldBe expectedKeyType
          entryType.fields("value") shouldBe expectedValueType
      }
      parsedToListExpression.evaluateSync[Any](customCtx) shouldBe expectedToListResult

      val parsedRoundTripExpression = parse[Any](mapExpression + ".toList.toMap", customCtx).validValue
      parsedRoundTripExpression.evaluateSync[Any](customCtx) shouldBe givenMap
      val roundTripTypeIsAGeneralizationOfGivenType =
        givenMapExpression.returnType canBeConvertedTo parsedRoundTripExpression.returnType
      roundTripTypeIsAGeneralizationOfGivenType shouldBe true
    }

  }

  test("should allow use no param method property accessor on unknown") {
    val customCtx = ctx.withVariable("unknownInt", ContainerOfUnknown("11"))
    val parsed    = parse[Any]("#unknownInt.value.toLongOrNull", customCtx).validValue
    parsed.evaluateSync[Any](customCtx) shouldBe 11
    parsed.evaluateSync[Any](customCtx) shouldBe 11
  }

  // This test is ignored as it was indeterministic and ugly, but it was used to verify race condition problems on
  // ParsedSpelExpression.getValue. Without the synchronized block inside its method the test would fail the majority of times
  ignore(
    "should not throw 'Failed to instantiate CompiledExpression' when getValue is called on ParsedSpelExpression by multiple threads"
  ) {
    val spelExpression =
      parse[LocalDateTime]("T(java.time.LocalDateTime).now().minusDays(14)", ctx).validValue.expression
        .asInstanceOf[SpelExpression]

    val threadPool                                        = Executors.newFixedThreadPool(1000)
    implicit val customExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(threadPool)

    // A promise to signal when an exception occurs
    val failurePromise = Promise[Unit]()

    val tasks = (1 to 10000).map { _ =>
      Future {
        try {
          Thread.sleep(100)
          // evaluate calls getValue on problematic SpelExpression object
          spelExpression.evaluate[LocalDateTime](Context("fooId"), Map.empty)
        } catch {
          // The real problematic exception is wrapped in SpelExpressionEvaluationException by evaluate method
          case e: SpelExpressionEvaluationException =>
            failurePromise.tryFailure(e.cause)
        }
      }
    }
    val firstFailureOrCompletion = Future.firstCompletedOf(Seq(Future.sequence(tasks), failurePromise.future))

    firstFailureOrCompletion.onComplete {
      case Success(_) =>
        println("All tasks completed successfully.")
        threadPool.shutdown()
      case Failure(e: IllegalStateException) if e.getMessage == "Failed to instantiate CompiledExpression" =>
        fail("Exception occurred due to race condition.", e)
        threadPool.shutdown()
      case Failure(e) =>
        fail("Unknown exception occurred", e)
        threadPool.shutdown()
    }
    Await.result(firstFailureOrCompletion, 15.seconds)
  }

}

case class SampleObject(list: java.util.List[SampleValue])

case class SampleValue(value: Int, anyObject: Any = "") {

  @Hidden
  def getSomeHiddenGetter: String = {
    "someHidden"
  }

}

object SimpleEnum extends Enumeration {
  // we must explicitly define Value class to recognize if type is matching
  class SimpleValue(name: String) extends Val(name)

  val One: SimpleValue = new SimpleValue("one")
  val Two: SimpleValue = new SimpleValue("two")
}

object SampleGlobalObject {
  val constant                         = 4
  def add(a: Int, b: Int): Int         = a + b
  def addLongs(a: Long, b: Long): Long = a + b
  // varargs annotation is needed to invoke Scala varargs from Java (including SpEL...)
  @varargs def addAll(a: Int*): Int                                            = a.sum
  def one()                                                                    = 1
  def now: LocalDateTime                                                       = LocalDateTime.now()
  def identityMap(map: java.util.Map[String, Any]): java.util.Map[String, Any] = map
  def stringList(arg: java.util.List[String]): Int                             = arg.size()
  def toAny(value: Any): Any                                                   = value
  def stringOnStringMap: java.util.Map[String, String] = Map("key1" -> "value1", "key2" -> "value2").asJava

  def methodWithPrimitiveParams(int: Int, long: Long, bool: Boolean): String = s"$int $long $bool"

  def someComparable: Comparable[Any] = ???

  @GenericType(typingFunction = classOf[GenericFunctionHelper])
  def genericFunction(a: Int, b: Boolean): Int = a + (if (b) 1 else 0)

  @GenericType(typingFunction = classOf[GenericFunctionVarArgHelper])
  @varargs
  def genericFunctionWithVarArg(a: Int, b: Boolean*): Int = a + b.count(identity)

  private case class GenericFunctionHelper() extends TypingFunction {

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] =
      Typed[Int].validNel

  }

  private case class GenericFunctionVarArgHelper() extends TypingFunction {

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] =
      Typed[Int].validNel

  }

}

class SampleObjectWithGetMethod(map: Map[String, Any]) {

  def get(field: String): Any = map.getOrElse(field, throw new IllegalArgumentException(s"No such field: $field"))

  def definedProperty: String = "123"

}
