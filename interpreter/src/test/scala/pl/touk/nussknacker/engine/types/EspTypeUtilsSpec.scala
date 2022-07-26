package pl.touk.nussknacker.engine.types

import io.circe.Decoder
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{AddPropertyNextToGetter, DoNothing, ReplaceGetterWithProperty}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, Documentation, Hidden, HideToString, ParamName}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo, Parameter}
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.types.TypesInformationExtractor._

import java.util
import java.util.regex.Pattern
import scala.annotation.meta.getter
import scala.concurrent.Future
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
      "foo" -> List(MethodInfo(List.empty, Typed(Integer.TYPE), "foo", None, varArgs = false)),
      "bar" -> List(MethodInfo(List.empty, Typed[String], "bar", None, varArgs = false)),
      "toString" -> List(MethodInfo(List(), Typed[String], "toString", None, varArgs = false)),
      "returnContext" -> List(MethodInfo(List(), Typed[Context], "returnContext", None, varArgs = false))
    )
  }

  test("should extract generic types from Option") {
    val classInfo = singleClassDefinition[ClassWithOptions]()
    classInfo.value.methods shouldBe Map(
      // generic type of Java type is properly read
      "longJavaOption" -> List(MethodInfo(List.empty, Typed[Long], "longJavaOption", None, varArgs = false)),
      // generic type of Scala type is erased - this case documents that behavior
      "longScalaOption" -> List(MethodInfo(List.empty, typing.Unknown, "longScalaOption", None, varArgs = false)),
      "toString" -> List(MethodInfo(List(), Typed[String], "toString", None, varArgs = false)),
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
          "toString" -> List(MethodInfo(List(), Typed[String], "toString", None, varArgs = false)),
          "foo" -> List(MethodInfo(List.empty, Typed(Integer.TYPE), "foo", None, varArgs = false))
        )
      }
    }
  }

  test("should extract parameters from embedded lists") {

    val typeUtils = singleClassAndItsChildrenDefinition[Embeddable]()

    typeUtils.find(_.clazzName == Typed[TestEmbedded]) shouldBe Some(ClazzDefinition(Typed.typedClass[TestEmbedded], Map(
      "string" -> List(MethodInfo(List(), Typed[String], "string", None, varArgs = false)),
      "javaList" -> List(MethodInfo(List(), Typed.fromDetailedType[java.util.List[String]], "javaList", None, varArgs = false)),
      "javaMap" -> List(MethodInfo(List(), Typed.fromDetailedType[java.util.Map[String, String]], "javaMap", None, varArgs = false)),
      "toString" -> List(MethodInfo(List(), Typed[String], "toString", None, varArgs = false))
    ), Map.empty))

  }

  test("should not discover hidden fields") {
    val typeUtils = singleClassDefinition[ClassWithHiddenFields]()

    typeUtils shouldBe Some(ClazzDefinition(Typed.typedClass[ClassWithHiddenFields], Map(
      "normalField" -> List(MethodInfo(List(), Typed[String], "normalField", None, varArgs = false)),
      "normalParam" -> List(MethodInfo(List(), Typed[String], "normalParam", None, varArgs = false)),
      "toString" -> List(MethodInfo(List(), Typed[String], "toString", None, varArgs = false))
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
          info.staticParameters shouldBe params
          info.staticResult shouldBe result
          info.name shouldBe name
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
      ("field2", List.empty, Typed[Long], Some(ScalaSampleDocumentedClass.field2Docs), false)
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
    methods.map(_.staticParameters.head.refClazz).toSet shouldEqual Set(Typed[Int], Typed[Boolean], Typed[String])
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

  test("should extract vaarargs") {
    val classDef = singleClassDefinition[JavaClassWithVarargs](ClassExtractionSettings.Default).value
    val methodDef = classDef.methods.get("addAllWithObjects").value
    methodDef should have length 1
    val method = methodDef.head
    method.staticParameters should have length 1
    method.staticParameters.head.refClazz shouldEqual Typed.fromDetailedType[Array[Object]]
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
