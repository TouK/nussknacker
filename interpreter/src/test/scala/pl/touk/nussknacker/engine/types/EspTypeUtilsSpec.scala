package pl.touk.nussknacker.engine.types

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import io.circe.Decoder
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, ExpressionParseError, GenericType, Signature, TypingFunction}
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{AddPropertyNextToGetter, DoNothing, ReplaceGetterWithProperty}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, Documentation, Hidden, HideToString, ParamName}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo, Parameter}
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.types.TypesInformationExtractor._

import java.util
import java.util.regex.Pattern
import scala.annotation.meta.getter
import scala.concurrent.Future
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
import scala.reflect.runtime.universe._

class EspTypeUtilsSpec extends FunSuite with Matchers with OptionValues {

  case class SampleClass(foo: Int, bar: String) extends SampleAbstractClass with SampleInterface {
    def returnContext: Context = null
    def decoder: Decoder[SampleClass] = null
    def classParam(parameter: Class[_]): String = null

  }

  class Returning {
    def futureOfList: Future[java.util.List[SampleClass]] = ???
  }

  case class Top(middle: Middle)

  case class Middle(bottom: Bottom)

  case class Bottom(someInt: Int)

  case class ClassWithOptions(longJavaOption: Option[java.lang.Long],
                              longScalaOption: Option[Long])

  test("should extract generic return type parameters") {

    val method = classOf[Returning].getMethod("futureOfList")

    val extractedType = EspTypeUtils.extractMethodReturnType(method)

    extractedType shouldBe Typed.fromDetailedType[Future[java.util.List[SampleClass]]]
  }

  test("should extract public fields from scala case class") {
    val sampleClassInfo = singleClassDefinition[SampleClass]()

    sampleClassInfo.value.methods shouldBe Map(
      "foo" -> List(StaticMethodInfo.fromParameterList(List.empty, Typed(Integer.TYPE), "foo", None, varArgs = false)),
      "bar" -> List(StaticMethodInfo.fromParameterList(List.empty, Typed[String], "bar", None, varArgs = false)),
      "toString" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "toString", None, varArgs = false)),
      "returnContext" -> List(StaticMethodInfo.fromParameterList(List(), Typed[Context], "returnContext", None, varArgs = false))
    )
  }

  test("should extract generic types from Option") {
    val classInfo = singleClassDefinition[ClassWithOptions]()
    classInfo.value.methods shouldBe Map(
      // generic type of Java type is properly read
      "longJavaOption" -> List(StaticMethodInfo.fromParameterList(List.empty, Typed[Long], "longJavaOption", None, varArgs = false)),
      // generic type of Scala type is erased - this case documents that behavior
      "longScalaOption" -> List(StaticMethodInfo.fromParameterList(List.empty, typing.Unknown, "longScalaOption", None, varArgs = false)),
      "toString" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "toString", None, varArgs = false)),
    )
  }

  test("should extract generic field") {
    val sampleClassInfo = singleClassDefinition[JavaClassWithGenericField]()

    sampleClassInfo.value.methods("list").head.staticResult shouldEqual Typed.fromDetailedType[java.util.List[String]]
  }

  test("should detect java beans and fields in java class") {
    def methods(strategy: PropertyFromGetterExtractionStrategy) =
      singleClassDefinition[JavaSampleClass](ClassExtractionSettings.Default.copy(propertyExtractionStrategy = strategy)).value.methods.keys.toSet

    val methodsForAddPropertyNextToGetter = methods(AddPropertyNextToGetter)
    methodsForAddPropertyNextToGetter   shouldEqual Set("foo", "bar", "getBeanProperty", "isBooleanProperty", "getNotProperty", "toString", "beanProperty", "booleanProperty")

    val methodsForReplaceGetterWithProperty = methods(ReplaceGetterWithProperty)
    methodsForReplaceGetterWithProperty shouldEqual Set("foo", "bar", "beanProperty", "booleanProperty", "getNotProperty", "toString")

    val methodsForDoNothing = methods(DoNothing)
    methodsForDoNothing shouldEqual Set("foo", "bar", "getBeanProperty", "isBooleanProperty", "getNotProperty", "toString")
  }

  test("should skip hidden properties") {
    val testTypes = Table(("type", "className"),
      (Typed[SampleClass], "SampleClass"),
      (Typed[JavaSampleClass], "JavaSampleClass")
    )

    val testClassPatterns = Table("classPattern",
      ".*SampleClass",
      ".*SampleAbstractClass",
      ".*SampleInterface"
    )

    forAll(testTypes) { (clazz, clazzName) =>
      forAll(testClassPatterns) { classPattern =>
        val infos = clazzAndItsChildrenDefinition(List(clazz))(ClassExtractionSettings.Default.copy(excludeClassMemberPredicates =
          ClassExtractionSettings.DefaultExcludedMembers ++ Seq(
          ClassMemberPatternPredicate(SuperClassPredicate(ClassPatternPredicate(Pattern.compile(classPattern))), Pattern.compile("ba.*")),
          ClassMemberPatternPredicate(SuperClassPredicate(ClassPatternPredicate(Pattern.compile(classPattern))), Pattern.compile("get.*")),
          ClassMemberPatternPredicate(SuperClassPredicate(ClassPatternPredicate(Pattern.compile(classPattern))), Pattern.compile("is.*")),
          ReturnMemberPredicate(ExactClassPredicate[Context], BasePackagePredicate("pl.touk.nussknacker.engine.types"))
        )))
        val sampleClassInfo = infos.find(_.clazzName.klass.getName.contains(clazzName)).get

        sampleClassInfo.methods shouldBe Map(
          "toString" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "toString", None, varArgs = false)),
          "foo" -> List(StaticMethodInfo.fromParameterList(List.empty, Typed(Integer.TYPE), "foo", None, varArgs = false))
        )
      }
    }
  }

  test("should extract parameters from embedded lists") {

    val typeUtils = singleClassAndItsChildrenDefinition[Embeddable]()

    typeUtils.find(_.clazzName == Typed[TestEmbedded]) shouldBe Some(ClazzDefinition(Typed.typedClass[TestEmbedded], Map(
      "string" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "string", None, varArgs = false)),
      "javaList" -> List(StaticMethodInfo.fromParameterList(List(), Typed.fromDetailedType[java.util.List[String]], "javaList", None, varArgs = false)),
      "javaMap" -> List(StaticMethodInfo.fromParameterList(List(), Typed.fromDetailedType[java.util.Map[String, String]], "javaMap", None, varArgs = false)),
      "toString" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "toString", None, varArgs = false))
    ), Map.empty))

  }

  test("should not discover hidden fields") {
    val typeUtils = singleClassDefinition[ClassWithHiddenFields]()

    typeUtils shouldBe Some(ClazzDefinition(Typed.typedClass[ClassWithHiddenFields], Map(
      "normalField" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "normalField", None, varArgs = false)),
      "normalParam" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "normalParam", None, varArgs = false)),
      "toString" -> List(StaticMethodInfo.fromParameterList(List(), Typed[String], "toString", None, varArgs = false))
    ), Map.empty))
  }

  test("should skip toString method when HideToString implemented") {
    val hiddenToStringClasses = Table("class", classOf[JavaBannedToStringClass], classOf[BannedToStringClass])
    forAll(hiddenToStringClasses) { EspTypeUtils.clazzDefinition(_)(ClassExtractionSettings.Default)
      .methods.keys shouldNot contain("toString")
    }
  }

  test("should break recursive discovery if hidden class found") {

    val extracted = TypesInformationExtractor.clazzAndItsChildrenDefinition(List(Typed[Top]))(ClassExtractionSettings.Default.copy(
      excludeClassPredicates = ClassExtractionSettings.DefaultExcludedClasses :+ ExactClassPredicate[Middle]
    ))
    extracted.find(_.clazzName == Typed[Top]) shouldBe 'defined
    extracted.find(_.clazzName == Typed[Middle]) shouldBe 'empty
    extracted.find(_.clazzName == Typed[Bottom]) shouldBe 'empty
  }

  class BannedToStringClass extends HideToString

  case class ScalaSampleDocumentedClass() {

    val field1: Long = 123L

    //we use this @getter annotation here, because vals in case classes are translated by default to private field and public getter method for that field
    //we just annotate public getter method (that will be created by scalac) for field2
    //more here: https://www.scala-lang.org/api/current/scala/annotation/meta/index.html
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
  }

  case class TestEmbedded(string: String, javaList: java.util.List[String], scalaList: List[String], javaMap: java.util.Map[String, String])

  class Embeddable {

    def data: Future[List[TestEmbedded]] = ???

  }

  case class ClassWithHiddenFields(@(Hidden @getter)imnothereCaseClassParam: String, normalParam: String) {

    @Hidden
    def imnothereMethod(par: String): String = par

    @(Hidden @getter)
    val imnothereField: String = ""

    val normalField: String = ""
  }

  object ScalaSampleDocumentedClass {
    final val field2Docs = "This is sample documentation for field2 field"
    final val bazDocs = "This is sample documentation for baz method"
    final val quxDocs = "This is sample documentation for qux method"
    final val headDocs = "This is sample documentation for head method"
  }

  test("should extract description and params from method") {
    val scalaClazzInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    val javaClazzInfo = singleClassDefinition[JavaSampleDocumentedClass]().value

    def checkMethodInfo(name: String,
                        params: List[Parameter],
                        result: TypingResult,
                        desc: Option[String],
                        varArgs: Boolean): Unit = {
      val scalaInfo :: Nil = scalaClazzInfo.methods(name)
      val javaInfo :: Nil = javaClazzInfo.methods(name)
      List(scalaInfo, javaInfo).foreach(info => {
          info.staticParametersWithSimpleVarArg shouldBe params
          info.staticResult shouldBe result
          info.description shouldBe desc
          info.varArgs shouldBe varArgs
        }
      )
    }

    val table = Table(
      ("methodName", "params", "result", "desc", "varArgs"),
      ("foo", List(param[String]("fooParam1")), Typed[Long], None, false),
      ("bar", List(param[Long]("barparam1")), Typed[String], None, false),
      ("baz", List(param[String]("bazparam1"), param[Int]("bazparam2")), Typed[Long], Some(ScalaSampleDocumentedClass.bazDocs), false),
      ("qux", List(param[String]("quxParam1")), Typed[Long], Some(ScalaSampleDocumentedClass.quxDocs), false),
      ("field1", List.empty, Typed[Long], None, false),
      ("field2", List.empty, Typed[Long], Some(ScalaSampleDocumentedClass.field2Docs), false),
      ("head", List(param[java.util.List[_]]("list")), Typed[Object], Some(ScalaSampleDocumentedClass.headDocs), false)
    )

    forAll(table)(checkMethodInfo)
  }

  test("enabled by default classes") {
    val emptyDef = singleClassAndItsChildrenDefinition[EmptyClass]()
    // We want to use boxed primitive classes even if they wont be discovered in any place
    val boxedIntDef = emptyDef.find(_.clazzName == Typed[Integer])
    boxedIntDef shouldBe defined
  }

  test("extract overloaded methods") {
    val cl = singleClassDefinition[ClassWithOverloadedMethods]().value
    val methods = cl.methods("method")
    methods should have size 3
    methods.map(_.staticParametersWithSimpleVarArg.head.refClazz).toSet shouldEqual Set(Typed[Int], Typed[Boolean], Typed[String])
  }

  test("hidden by default classes") {
    val metaSpelDef = singleClassAndItsChildrenDefinition[ServiceWithMetaSpelParam]()
    // These params are used programmable - user can't create instance of this type
    metaSpelDef.exists(_.clazzName == Typed[SpelExpressionRepr]) shouldBe false
  }

  val listMethods = Table("methodName", "indexOf", "contains", "isEmpty", "size")
  val mapMethods = Table("methodName", "get", "isEmpty", "size", "values")
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

  test("should hide some ugly presented methods") {
    val classDef = singleClassDefinition[ClassWithWeirdTypesInMethods]().value
    classDef.methods.get("methodWithDollar$") shouldBe empty
    classDef.methods.get("normalMethod") shouldBe defined
  }

  test("should extract varargs") {
    val classDef = singleClassDefinition[JavaClassWithVarargs](ClassExtractionSettings.Default).value
    val methodDef = classDef.methods.get("addAllWithObjects").value
    methodDef should have length 1
    val method = methodDef.head
    method.staticParametersWithFullVarArg should have length 1
    method.staticParametersWithFullVarArg.head.refClazz shouldEqual Typed.fromDetailedType[Array[Object]]
  }

  private def checkApplyFunction(classes: List[ClazzDefinition],
                                 name: String,
                                 arguments: List[TypingResult],
                                 expected: ValidatedNel[String, TypingResult]): Unit =
    classes.map(clazz => {
      val info :: Nil = clazz.methods(name)
      info.computeResultType(arguments).leftMap(_.map(_.message)) shouldBe expected
    })

  test("should correctly calculate result types on correct inputs") {
    val javaClassInfo = singleClassDefinition[JavaSampleDocumentedClass]().value
    val scalaClassInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    def checkApplyFunctionValid(name: String, arguments: List[TypingResult], result: TypingResult): Unit =
      checkApplyFunction(List(scalaClassInfo, javaClassInfo), name, arguments, result.validNel)

    val typedList = Typed.genericTypeClass[java.util.List[_]](List(Typed[String]))
    val typedMap = TypedObjectTypingResult(List("a" -> Typed[Int], "b" -> Typed[String]))

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
      ("head", List(Typed.genericTypeClass[java.util.List[_]](List(typedMap))), typedMap)
    )

    forAll(table)(checkApplyFunctionValid)
  }

  test("should correctly handle illegal input types in generic functions") {
    val javaClassInfo = singleClassDefinition[JavaSampleDocumentedClass]().value
    val scalaClassInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    def checkApplyFunctionInvalid(name: String, arguments: List[TypingResult], expected: List[TypingResult]): Unit =
      checkApplyFunction(
        List(scalaClassInfo, javaClassInfo),
        name,
        arguments,
        new ArgumentTypeError(
          new Signature(name, arguments, None),
          List(new Signature(name, expected, None))
        ).message.invalidNel
      )

    val table = Table(
      ("name", "arguments", "expected"),
      ("foo", List(), List(Typed[String])),
      ("foo", List(Typed[Int]), List(Typed[String])),
      ("foo", List(Typed[String], Typed[Int]), List(Typed[String])),
      ("baz", List(), List(Typed[String], Typed[Int])),
      ("baz", List(Typed[Int]), List(Typed[String], Typed[Int])),
      ("baz", List(Typed[String]), List(Typed[String], Typed[Int])),
      ("baz", List(Typed[String], Typed[Int], Typed[Double]), List(Typed[String], Typed[Int])),
      ("field1", List(Typed[Int]), List()),
      ("head", List(Typed[Int]), List(Typed[List[Object]])),
      ("head", List(Typed[Set[_]]), List(Typed[List[Object]])),
      ("head", List(Typed[Map[_, _]]), List(Typed[List[Object]])),
      ("head", List(), List(Typed[List[Object]])),
      ("head", List(Typed[List[_]], Typed[Int]), List(Typed[List[Object]]))
    )

    forAll(table)(checkApplyFunctionInvalid)
  }

  class EmptyClass {
    def invoke(): Unit = ???
  }

  class ClassWithWeirdTypesInMethods {
    def methodReturningArray: Array[Int] = ???
    def methodWithDollar$: String = ???
    def normalMethod: String = ???
  }

  class ClassWithOverloadedMethods {
    def method(i: Int): String = ???
    def method(i: String): String = ???
    def method(i: Boolean): String = ???
  }

  class ServiceWithMetaSpelParam {
    def invoke(@ParamName("expression") expr: SpelExpressionRepr): Unit = ???
  }

  private def param[T: TypeTag](name: String): Parameter = {
    Parameter(name, Typed.fromDetailedType[T])
  }

  private def singleClassDefinition[T: TypeTag](settings: ClassExtractionSettings = ClassExtractionSettings.Default): Option[ClazzDefinition] = {
    val ref = Typed.fromDetailedType[T]
    // ClazzDefinition has clazzName with generic parameters but they are always empty so we need to compare name without them
    clazzAndItsChildrenDefinition(List(Typed(ref)))(settings).find(_.clazzName.klass == ref.asInstanceOf[TypedClass].klass)
  }

  private def singleClassAndItsChildrenDefinition[T: TypeTag](settings: ClassExtractionSettings = ClassExtractionSettings.Default) = {
    val ref = Typed.fromDetailedType[T]
    clazzAndItsChildrenDefinition(List(Typed(ref)))(settings)
  }

}

// This class cannot be declared inside non-static class because it needs to
// be constructable without instances of other classes.
private class HeadHelper extends TypingFunction {
  private val listClass = classOf[java.util.List[_]]

  private def error(arguments: List[TypingResult]): ExpressionParseError =
    new ArgumentTypeError(
      new Signature("head", arguments, None),
      List(new Signature("head", List(Typed.fromDetailedType[List[Object]]), None))
    )

  override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
    case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
    case TypedClass(`listClass`, _) :: Nil => throw new AssertionError("Lists must have one parameter")
    case _ => error(arguments).invalidNel
  }
}