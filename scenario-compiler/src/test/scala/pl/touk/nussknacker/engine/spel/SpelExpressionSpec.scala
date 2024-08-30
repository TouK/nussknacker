package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalacheck.Gen
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictInstance}
import pl.touk.nussknacker.engine.api.generics.{
  ExpressionParseError,
  GenericFunctionTypingError,
  GenericType,
  TypingFunction
}
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, _}
import pl.touk.nussknacker.engine.api.{Context, NodeId, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, JavaClassWithVarargs}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.{
  IllegalProjectionSelectionError,
  InvalidMethodReference,
  TypeReferenceError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.{
  NoPropertyError,
  UnknownClassError,
  UnknownMethodError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.OperatorError._
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.UnsupportedOperationError.ArrayConstructorError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.{ArgumentTypeError, ExpressionTypeError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.{Flavour, Standard}
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.math.{BigDecimal, BigInteger}
import java.nio.charset.Charset
import java.time.chrono.ChronoLocalDate
import java.time.{LocalDate, LocalDateTime}
import java.util
import java.util.{Collections, Currency, Locale, Optional, UUID}
import scala.annotation.varargs
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

class SpelExpressionSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage with OptionValues {

  private implicit class ValidatedExpressionOps[E](validated: Validated[E, TypedExpression]) {
    def validExpression: CompiledExpression = validated.validValue.expression
  }

  private implicit class EvaluateSync(expression: CompiledExpression) {
    def evaluateSync[T](ctx: Context = ctx): T = expression.evaluate(ctx, Map.empty)
  }

  private implicit val nid: NodeId = NodeId("")

  private val bigValue = BigDecimal.valueOf(4187338076L)

  private val testValue = Test("1", 2, List(Test("3", 4), Test("5", 6)).asJava, bigValue)

  private val ctx = Context("abc").withVariables(
    Map(
      "obj"         -> testValue,
      "strVal"      -> "",
      "mapValue"    -> Map("foo" -> "bar").asJava,
      "array"       -> Array("a", "b"),
      "intArray"    -> Array(1, 2, 3),
      "nestedArray" -> Array(Array(1, 2), Array(3, 4))
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
      bigValue: BigDecimal = BigDecimal.valueOf(0L)
  )

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
      .map(_.objType.klass)
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
      classOf[SampleValue],
      Class.forName("pl.touk.nussknacker.engine.spel.SampleGlobalObject")
    )
    ClassDefinitionSet.forClasses(typesFromGlobalVariables ++ customClasses: _*)
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
    parse[BigInteger](
      "T(java.math.BigInteger).valueOf(1L)",
      staticMethodInvocationsChecking = false,
      methodExecutionForUnknownAllowed = true
    ).validExpression.evaluateSync[BigInteger](ctx) should equal(BigInteger.ONE)
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
    val parsed             = parse[Any]("#processHelper.add", ctxWithGlobal)
    val expectedValidation = Invalid("There is no property 'add' in type: SampleGlobalObject")
    parsed.isInvalid shouldBe true
    parsed.leftMap(_.head).leftMap(_.message) shouldEqual expectedValidation
  }

  test("handle big decimals") {
    bigValue.compareTo(BigDecimal.valueOf(50 * 1024 * 1024)) should be > 0
    bigValue.compareTo(BigDecimal.valueOf(50 * 1024 * 1024L)) should be > 0
    parse[Any]("#obj.bigValue").validExpression.evaluateSync[BigDecimal](ctx) should equal(bigValue)
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
      .evaluateSync[String]() shouldBe "alamakota 444"
    parse[String](
      "alamakota #{444 + #obj.value} #{#mapValue.foo}",
      ctx,
      flavour = SpelExpressionParser.Template
    ).validExpression.evaluateSync[String]() shouldBe "alamakota 446 bar"
  }

  test("evaluates empty template as empty string") {
    parse[String]("", ctx, flavour = SpelExpressionParser.Template).validExpression.evaluateSync[String]() shouldBe ""
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
    parse[Locale]("'PL'", ctx).validExpression.evaluateSync[Locale](ctx) shouldBe Locale.forLanguageTag("PL")
    parse[LocalDate]("'2007-12-03'", ctx).validExpression.evaluateSync[Locale](ctx) shouldBe LocalDate.parse(
      "2007-12-03"
    )
    parse[ChronoLocalDate]("'2007-12-03'", ctx).validExpression.evaluateSync[Locale](ctx) shouldBe LocalDate.parse(
      "2007-12-03"
    )
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

  test("should not validate array constructor") {
    List("new String[]", "new String[ ]", "new String[0]", "new String[#invalidRef]", "new String[invalidSyntax]").map(
      illegalExpr => parse[Any](illegalExpr, ctx).invalidValue shouldBe NonEmptyList.one(ArrayConstructorError)
    )
  }

  test("should return correct type in array projection") {
    val parsed              = parse[Any]("#array.![#this]", ctx)
    val evaluated           = parsed.validExpression.evaluateSync[Any](ctx)
    val expectedRuntimeType = Typed.genericTypeClass(classOf[Array[String]], List(Typed.typedClass(classOf[String])))

    parsed.validValue.returnType shouldBe expectedRuntimeType
    evaluated shouldBe Array("a", "b")
  }

  test("should return error on String projection") {
    parse[Any]("'ab'.![#this]", ctx).invalidValue.toList.headOption.value shouldBe a[IllegalProjectionSelectionError]
  }

  test("should convert array to list when passing arg which type should be list") {
    parse[Any]("T(java.lang.String).join(',', #array)", ctx).validExpression
      .evaluateSync[String](ctx) shouldBe "a,b"
  }

  test("should allow using list methods on array projection") {
    parse[Any]("'a,b'.split(',').![#this].isEmpty()", ctx).validExpression
      .evaluateSync[Boolean](ctx) shouldBe false
  }

  test("should allow using list methods on array") {
    parse[Any]("#array.isEmpty()", ctx).validExpression.evaluateSync[Boolean](ctx) shouldBe false
    parse[Any]("#intArray.isEmpty()", ctx).validExpression.evaluateSync[Boolean](ctx) shouldBe false
  }

  test("should allow using list methods on nested arrays") {
    parse[Any]("#nestedArray.![#this.isEmpty()]", ctx).validExpression
      .evaluateSync[Any](ctx) shouldBe Array(false, false)
  }

}

case class SampleObject(list: java.util.List[SampleValue])

case class SampleValue(value: Int, anyObject: Any = "")

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
