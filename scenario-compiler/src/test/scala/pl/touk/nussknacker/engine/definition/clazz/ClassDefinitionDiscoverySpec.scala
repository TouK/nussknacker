package pl.touk.nussknacker.engine.definition.clazz

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import io.circe.Decoder
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.generics._
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{
  AddPropertyNextToGetter,
  DoNothing,
  ReplaceGetterWithProperty
}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, _}
import pl.touk.nussknacker.engine.api.{Context, Documentation, Hidden, HideToString, ParamName}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor.MethodExtensions
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils.{DefaultExtractor, createDiscovery}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ArgumentTypeError
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util
import java.util.regex.Pattern
import scala.annotation.meta.getter
import scala.annotation.varargs
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

class ClassDefinitionDiscoverySpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with ValidatedValuesDetailedMessage {

  case class SampleClass(foo: Int, bar: String) extends SampleAbstractClass with SampleInterface {
    def returnContext: Context                  = null
    def decoder: Decoder[SampleClass]           = null
    def classParam(parameter: Class[_]): String = null

  }

  class Returning {
    def futureOfList: Future[java.util.List[SampleClass]] = ???
  }

  case class Top(middle: Middle)

  case class Middle(bottom: Bottom)

  case class Bottom(someInt: Int)

  case class ClassWithOptions(longJavaOption: Option[java.lang.Long], longScalaOption: Option[Long])

  test("should extract generic return type parameters") {

    val method = classOf[Returning].getMethod("futureOfList")

    val extractedType = method.returnType()

    extractedType shouldBe Typed.fromDetailedType[Future[java.util.List[SampleClass]]]
  }

  test("should extract public fields from scala case class") {
    val sampleClassInfo = singleClassDefinition[SampleClass]()

    sampleClassInfo.value.methods shouldBe Map(
      "foo"      -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed(Integer.TYPE)), "foo", None)),
      "bar"      -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "bar", None)),
      "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
      "returnContext" -> List(
        StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[Context]), "returnContext", None)
      )
    )
  }

  test("should extract generic types from Option") {
    val classInfo = singleClassDefinition[ClassWithOptions]()
    classInfo.value.methods shouldBe Map(
      // generic type of Java type is properly read
      "longJavaOption" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[Long]), "longJavaOption", None)),
      // generic type of Scala type is erased - this case documents that behavior
      "longScalaOption" -> List(
        StaticMethodDefinition(MethodTypeInfo(Nil, None, typing.Unknown), "longScalaOption", None)
      ),
      "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
    )
  }

  test("should extract generic field") {
    val sampleClassInfo = singleClassDefinition[JavaClassWithGenericField]()

    sampleClassInfo.value.methods("list").head.signatures.head.result shouldEqual Typed
      .fromDetailedType[java.util.List[String]]
  }

  test("should detect java beans and fields in java class") {
    def methods(strategy: PropertyFromGetterExtractionStrategy) =
      singleClassDefinition[JavaSampleClass](
        ClassExtractionSettings.Default.copy(propertyExtractionStrategy = strategy)
      ).value.methods.keys.toSet

    val methodsForAddPropertyNextToGetter = methods(AddPropertyNextToGetter)
    methodsForAddPropertyNextToGetter shouldEqual Set(
      "foo",
      "bar",
      "getBeanProperty",
      "isBooleanProperty",
      "getNotProperty",
      "toString",
      "beanProperty",
      "booleanProperty"
    )

    val methodsForReplaceGetterWithProperty = methods(ReplaceGetterWithProperty)
    methodsForReplaceGetterWithProperty shouldEqual Set(
      "foo",
      "bar",
      "beanProperty",
      "booleanProperty",
      "getNotProperty",
      "toString"
    )

    val methodsForDoNothing = methods(DoNothing)
    methodsForDoNothing shouldEqual Set(
      "foo",
      "bar",
      "getBeanProperty",
      "isBooleanProperty",
      "getNotProperty",
      "toString"
    )
  }

  test("should skip hidden properties") {
    val testTypes =
      Table(("type", "className"), (Typed[SampleClass], "SampleClass"), (Typed[JavaSampleClass], "JavaSampleClass"))

    val testClassPatterns = Table("classPattern", ".*SampleClass", ".*SampleAbstractClass", ".*SampleInterface")

    forAll(testTypes) { (clazz, clazzName) =>
      forAll(testClassPatterns) { classPattern =>
        val infos = createDiscovery(
          ClassExtractionSettings.Default.copy(excludeClassMemberPredicates =
            ClassExtractionSettings.DefaultExcludedMembers ++ Seq(
              MemberNamePatternPredicate(
                SuperClassPredicate(ClassPatternPredicate(Pattern.compile(classPattern))),
                Pattern.compile("ba.*")
              ),
              MemberNamePatternPredicate(
                SuperClassPredicate(ClassPatternPredicate(Pattern.compile(classPattern))),
                Pattern.compile("get.*")
              ),
              MemberNamePatternPredicate(
                SuperClassPredicate(ClassPatternPredicate(Pattern.compile(classPattern))),
                Pattern.compile("is.*")
              ),
              ReturnMemberPredicate(
                ExactClassPredicate[Context],
                BasePackagePredicate("pl.touk.nussknacker.engine.definition.clazz")
              )
            )
          )
        ).discoverClassesFromTypes(List(clazz))
        val sampleClassInfo = infos.find(_.getClazz.getName.contains(clazzName)).get

        sampleClassInfo.methods shouldBe Map(
          "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
          "foo"      -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed(Integer.TYPE)), "foo", None))
        )
      }
    }
  }

  test("should extract parameters from embedded lists") {

    val typeUtils = singleClassAndItsChildrenDefinition[Embeddable]()

    typeUtils.find(_.clazzName == Typed[TestEmbedded]) shouldBe Some(
      ClassDefinition(
        Typed.typedClass[TestEmbedded],
        Map(
          "string" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "string", None)),
          "javaList" -> List(
            StaticMethodDefinition(
              MethodTypeInfo(Nil, None, Typed.fromDetailedType[java.util.List[String]]),
              "javaList",
              None
            )
          ),
          "javaMap" -> List(
            StaticMethodDefinition(
              MethodTypeInfo(Nil, None, Typed.fromDetailedType[java.util.Map[String, String]]),
              "javaMap",
              None
            )
          ),
          "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None))
        ),
        Map.empty
      )
    )

  }

  test("should not discover hidden fields") {
    val typeUtils = singleClassDefinition[ClassWithHiddenFields]()

    typeUtils shouldBe Some(
      ClassDefinition(
        Typed.typedClass[ClassWithHiddenFields],
        Map(
          "normalField" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "normalField", None)),
          "normalParam" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "normalParam", None)),
          "toString"    -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None))
        ),
        Map.empty
      )
    )
  }

  test("should skip toString method when HideToString implemented") {
    val hiddenToStringClasses = Table("class", classOf[JavaBannedToStringClass], classOf[BannedToStringClass])
    forAll(hiddenToStringClasses) {
      DefaultExtractor.extract(_).methods.keys shouldNot contain("toString")
    }
  }

  test("should break recursive discovery if hidden class found") {
    val extracted = createDiscovery(
      ClassExtractionSettings.Default.copy(
        excludeClassPredicates = ClassExtractionSettings.DefaultExcludedClasses :+ ExactClassPredicate[Middle]
      )
    ).discoverClassesFromTypes(List(Typed[Top]))
    extracted.find(_.clazzName == Typed[Top]) shouldBe Symbol("defined")
    extracted.find(_.clazzName == Typed[Middle]) shouldBe Symbol("empty")
    extracted.find(_.clazzName == Typed[Bottom]) shouldBe Symbol("empty")
  }

  class BannedToStringClass extends HideToString

  case class ScalaSampleDocumentedClass() {

    val field1: Long = 123L

    // we use this @getter annotation here, because vals in case classes are translated by default to private field and public getter method for that field
    // we just annotate public getter method (that will be created by scalac) for field2
    // more here: https://www.scala-lang.org/api/current/scala/annotation/meta/index.html
    @(Documentation @getter)(description = ScalaSampleDocumentedClass.field2Docs)
    val field2: Long = 234L

    def foo(fooParam1: String): Long = {
      0L
    }

    def bar(@ParamName("barparam1") barparam1: Long): String = {
      ""
    }

    @Documentation(description = ScalaSampleDocumentedClass.bazDocs)
    def baz(@ParamName("bazparam1") bazparam1: String, @ParamName("bazparam2") bazparam2: Int): Long = {
      0L
    }

    @Documentation(description = ScalaSampleDocumentedClass.quxDocs)
    def qux(quxParam1: String): Long = {
      0L
    }

    @Documentation(description = ScalaSampleDocumentedClass.headDocs)
    @GenericType(typingFunction = classOf[HeadHelper])
    def head[T >: Null](list: java.util.List[T]): T =
      list.asScala.headOption.orNull

    @Documentation(description = ScalaSampleDocumentedClass.maxDocs)
    @GenericType(typingFunction = classOf[MaxHelper])
    @varargs
    def max[T <: Number](args: T*): T =
      args.maxBy(_.doubleValue())

  }

  case class TestEmbedded(
      string: String,
      javaList: java.util.List[String],
      scalaList: List[String],
      javaMap: java.util.Map[String, String]
  )

  class Embeddable {

    def data: Future[java.util.List[TestEmbedded]] = ???

  }

  case class ClassWithHiddenFields(@(Hidden @getter) imnothereCaseClassParam: String, normalParam: String) {

    @Hidden
    def imnothereMethod(par: String): String = par

    @(Hidden @getter)
    val imnothereField: String = ""

    val normalField: String = ""
  }

  object ScalaSampleDocumentedClass {
    final val field2Docs = "This is sample documentation for field2 field"
    final val bazDocs    = "This is sample documentation for baz method"
    final val quxDocs    = "This is sample documentation for qux method"
    final val headDocs   = "This is sample documentation for head method"
    final val maxDocs    = "This is sample documentation for max method"
  }

  test("should extract description and params from method") {
    val scalaClazzDefinition = singleClassDefinition[ScalaSampleDocumentedClass]().value

    val javaClazzDefinition = singleClassDefinition[JavaSampleDocumentedClass]().value

    def checkMethod(
        name: String,
        params: List[Parameter],
        result: TypingResult,
        desc: Option[String],
        varArgs: Boolean
    ): Unit = {
      val scalaMethod :: Nil = scalaClazzDefinition.methods(name)
      val javaMethod :: Nil  = javaClazzDefinition.methods(name)
      List(scalaMethod, javaMethod).foreach(method => {
        method.signatures.head shouldBe MethodTypeInfo.fromList(params, varArgs, result)
        method.description shouldBe desc
        method.signatures.head.varArg.isDefined shouldBe varArgs
      })
    }

    val table = Table(
      ("methodName", "params", "result", "desc", "varArgs"),
      ("foo", List(param[String]("fooParam1")), Typed[Long], None, false),
      ("bar", List(param[Long]("barparam1")), Typed[String], None, false),
      (
        "baz",
        List(param[String]("bazparam1"), param[Int]("bazparam2")),
        Typed[Long],
        Some(ScalaSampleDocumentedClass.bazDocs),
        false
      ),
      ("qux", List(param[String]("quxParam1")), Typed[Long], Some(ScalaSampleDocumentedClass.quxDocs), false),
      ("field1", List.empty, Typed[Long], None, false),
      ("field2", List.empty, Typed[Long], Some(ScalaSampleDocumentedClass.field2Docs), false),
      ("head", List(param[java.util.List[_]]("list")), Typed[Object], Some(ScalaSampleDocumentedClass.headDocs), false),
      ("max", List(param[Array[Number]]("args")), Typed[Number], Some(ScalaSampleDocumentedClass.maxDocs), true)
    )

    forAll(table)(checkMethod)
  }

  test("enabled by default classes") {
    val emptyDef = singleClassAndItsChildrenDefinition[EmptyClass]()
    // We want to use boxed primitive classes even if they wont be discovered in any place
    val boxedIntDef = emptyDef.find(_.clazzName == Typed[Integer])
    boxedIntDef shouldBe defined
  }

  test("extract overloaded methods") {
    val cl      = singleClassDefinition[ClassWithOverloadedMethods]().value
    val methods = cl.methods("method")
    methods should have size 3
    methods.map(_.signatures.head.noVarArgs.head.refClazz).toSet shouldEqual Set(
      Typed[Int],
      Typed[Boolean],
      Typed[String]
    )
  }

  test("hidden by default classes") {
    val metaSpelDef = singleClassAndItsChildrenDefinition[ServiceWithMetaSpelParam]()
    // These params are used programmable - user can't create instance of this type
    metaSpelDef.exists(_.clazzName == Typed[SpelExpressionRepr]) shouldBe false
  }

  val listMethods   = Table("methodName", "indexOf", "contains", "isEmpty", "size")
  val mapMethods    = Table("methodName", "get", "isEmpty", "size", "values")
  val optionMethods = Table("methodName", "get", "contains", "isEmpty")

  test("should extract basic methods from standard collection types") {
    forAll(mapMethods) { methodName =>
      val javaMapDef = singleClassDefinition[util.Map[_, _]]().value
      javaMapDef.methods.get(methodName) shouldBe defined

    }
    forAll(listMethods) { methodName =>
      val javaListDef = singleClassDefinition[util.List[_]]().value
      javaListDef.methods.get(methodName) shouldBe defined
    }

  }

  test("should handle generic params in maps") {
    val javaMapDef = DefaultExtractor.extract(classOf[util.Map[_, _]])
    val getMethodReturnType = javaMapDef.methods
      .get("get")
      .value
      .head
      .computeResultType(Typed.fromDetailedType[util.Map[String, String]], List(Typed[String]))
      .validValue
    getMethodReturnType shouldBe Typed[String]
  }

  test("should hide some ugly presented methods") {
    val classDef = singleClassDefinition[ClassWithWeirdTypesInMethods]().value
    classDef.methods.get("methodWithDollar$") shouldBe empty
    classDef.methods.get("normalMethod") shouldBe defined
  }

  test("should extract varargs") {
    val classDef  = singleClassDefinition[JavaClassWithVarargs](ClassExtractionSettings.Default).value
    val methodDef = classDef.methods.get("addAllWithObjects").value
    methodDef should have length 1
    val method = methodDef.head
    method.signatures.head.varArg shouldBe Some(Parameter("values", Unknown))
  }

  private def checkApplyFunction(
      classes: List[ClassDefinition],
      name: String,
      arguments: List[TypingResult],
      expected: ValidatedNel[String, TypingResult]
  ): Unit =
    classes.map(clazz => {
      val method :: Nil = clazz.methods(name)
      method.computeResultType(Unknown, arguments).leftMap(_.map(_.message)) shouldBe expected
    })

  test("should correctly calculate result types on correct inputs") {
    val javaClassInfo  = singleClassDefinition[JavaSampleDocumentedClass]().value
    val scalaClassInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    def checkApplyFunctionValid(name: String, arguments: List[TypingResult], result: TypingResult): Unit =
      checkApplyFunction(List(scalaClassInfo, javaClassInfo), name, arguments, result.validNel)

    val typedList = Typed.genericTypeClass[java.util.List[_]](List(Typed[String]))
    val typedMap  = Typed.record(Map("a" -> Typed[Int], "b" -> Typed[String]))

    val table = Table(
      ("name", "arguments", "result"),
      ("foo", List(Typed[String]), Typed[Long]),
      ("bar", List(Typed[Long]), Typed[String]),
      ("baz", List(Typed[String], Typed[Int]), Typed[Long]),
      ("qux", List(Typed[String]), Typed[Long]),
      ("field1", List(), Typed[Long]),
      ("field2", List(), Typed[Long]),
      ("head", List(Typed.genericTypeClass[java.util.List[_]](List(Typed[Int]))), Typed[Int]),
      ("head", List(Typed.genericTypeClass[java.util.List[_]](List(typedList))), typedList),
      ("head", List(Typed.genericTypeClass[java.util.List[_]](List(typedMap))), typedMap),
      ("max", List(Typed[Double]), Typed[Double]),
      ("max", List(Typed[Int], Typed[Int], Typed[Int]), Typed[Int]),
      ("max", List(Typed[Int], Typed[Double], Typed[Long]), Typed[Number])
    )

    forAll(table)(checkApplyFunctionValid)
  }

  test("should correctly handle illegal input types in generic functions") {
    val javaClassInfo  = singleClassDefinition[JavaSampleDocumentedClass]().value
    val scalaClassInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    def checkApplyFunctionInvalid(
        name: String,
        arguments: List[TypingResult],
        expected: List[TypingResult],
        expectedVarArg: Option[TypingResult]
    ): Unit =
      checkApplyFunction(
        List(scalaClassInfo, javaClassInfo),
        name,
        arguments,
        ArgumentTypeError(
          name,
          Signature(arguments, None),
          NonEmptyList.one(Signature(expected, expectedVarArg))
        ).message.invalidNel
      )

    val table = Table(
      ("name", "arguments", "expected", "expectedVarArg"),
      ("foo", List(), List(Typed[String]), None),
      ("foo", List(Typed[Int]), List(Typed[String]), None),
      ("foo", List(Typed[String], Typed[Int]), List(Typed[String]), None),
      ("baz", List(), List(Typed[String], Typed[Int]), None),
      ("baz", List(Typed[Int]), List(Typed[String], Typed[Int]), None),
      ("baz", List(Typed[String]), List(Typed[String], Typed[Int]), None),
      ("baz", List(Typed[String], Typed[Int], Typed[Double]), List(Typed[String], Typed[Int]), None),
      ("field1", List(Typed[Int]), List(), None),
      ("head", List(Typed[Int]), List(Typed[List[Object]]), None),
      ("head", List(Typed[Set[_]]), List(Typed[List[Object]]), None),
      ("head", List(Typed[Map[_, _]]), List(Typed[List[Object]]), None),
      ("head", List(), List(Typed[List[Object]]), None),
      ("head", List(Typed[List[_]], Typed[Int]), List(Typed[List[Object]]), None),
      ("max", List(Typed[String]), List(), Some(Typed[Number])),
      ("max", List(Typed[Int], Typed[String], Typed[Int]), List(), Some(Typed[Number]))
    )

    forAll(table)(checkApplyFunctionInvalid)
  }

  test("should correctly handle custom errors from generic functions") {
    val javaClassInfo  = singleClassDefinition[JavaSampleDocumentedClass]().value
    val scalaClassInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    checkApplyFunction(
      List(scalaClassInfo, javaClassInfo),
      "max",
      List(),
      "Max must have at least one argument".invalidNel
    )
  }

  test("should filter generic types") {
    val javaClassInfo = singleClassDefinition[JavaClassWithFilteredMethod]().value
    javaClassInfo.methods.keys shouldNot contain("notVisible")
  }

  class EmptyClass {
    def invoke(): Unit = ???
  }

  class ClassWithWeirdTypesInMethods {
    def methodReturningArray: Array[Int] = ???
    def methodWithDollar$ : String       = ???
    def normalMethod: String             = ???
  }

  class ClassWithOverloadedMethods {
    def method(i: Int): String     = ???
    def method(i: String): String  = ???
    def method(i: Boolean): String = ???
  }

  class ServiceWithMetaSpelParam {
    def invoke(@ParamName("expression") expr: SpelExpressionRepr): Unit = ???
  }

  private def param[T: TypeTag](name: String): Parameter = {
    Parameter(name, Typed.fromDetailedType[T])
  }

  private def singleClassDefinition[T: TypeTag](
      settings: ClassExtractionSettings = ClassExtractionSettings.Default
  ): Option[ClassDefinition] = {
    val ref = Typed.fromDetailedType[T]
    // ClazzDefinition has clazzName with generic parameters but they are always empty so we need to compare name without them
    createDiscovery(settings).discoverClassesFromTypes(List(ref)).find(_.getClazz == ref.asInstanceOf[TypedClass].klass)
  }

  private def singleClassAndItsChildrenDefinition[T: TypeTag](
      settings: ClassExtractionSettings = ClassExtractionSettings.Default
  ) = {
    val ref = Typed.fromDetailedType[T]
    createDiscovery(settings).discoverClassesFromTypes(List(ref))
  }

}

// This class cannot be declared inside non-static class because it needs to
// be constructable without instances of other classes.
private class HeadHelper extends TypingFunction {
  private val listClass = classOf[java.util.List[_]]

  override def computeResultType(
      arguments: List[TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
    case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
    case TypedClass(`listClass`, _) :: Nil        => throw new AssertionError("Lists must have one parameter")
    case _                                        => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

}

private class MaxHelper extends TypingFunction {

  override def computeResultType(
      arguments: List[TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, TypingResult] = {
    if (arguments.isEmpty)
      return GenericFunctionTypingError.OtherError("Max must have at least one argument").invalidNel

    arguments
      .reduce(CommonSupertypeFinder.Default.commonSupertype)
      .validNel
  }

}
