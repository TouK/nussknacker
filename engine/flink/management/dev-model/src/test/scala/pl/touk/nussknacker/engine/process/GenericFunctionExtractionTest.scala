package pl.touk.nussknacker.engine.process

import cats.implicits.catsSyntaxValidatedId
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.TypeInfos.{MethodInfo, Parameter}
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData

class GenericFunctionExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = LocalModelData(ConfigFactory.load(), new DevProcessConfigCreator)
  protected override val outputResource = "/extractedTypes/devCreator.json"

  private val genericFunctionsClassName = "GenericHelperFunction$"

  private val genericFunctions = ProcessDefinitionExtractor.extractTypes(model.processWithObjectsDefinition)
    .find(_.clazzName.klass.getSimpleName == genericFunctionsClassName)
    .getOrElse(fail(s"Expected to find class $genericFunctionsClassName"))

  private val extractTypeFunction = extractMethod("extractType")
  private val headFunction = extractMethod("head")

  private def extractMethod(methodName: String): MethodInfo = {
    val headList = genericFunctions
      .methods
      .getOrElse(methodName, fail(s"Expected to find method $methodName"))
    headList should have size 1
    val head :: Nil = headList
    head
  }

  test("should extract correct number of functions") {
    // Includes "toString"
    genericFunctions.methods should have size 3
  }

  test("should extract function information") {
    extractTypeFunction.staticParameters shouldBe List(Parameter("example of desired type", Typed(Typed[Int], Typed[String])))
    extractTypeFunction.staticResult shouldBe Typed(Typed.fromInstance("OK: Int"), Typed.fromInstance("OK: String"))
    extractTypeFunction.varArgs shouldBe false
    extractTypeFunction.description shouldBe Some("extracts type of given object")

    headFunction.staticParameters shouldBe List(Parameter("list", Typed.genericTypeClass[java.util.List[_]](List(Unknown))))
    headFunction.staticResult shouldBe Unknown
    headFunction.varArgs shouldBe false
    headFunction.description shouldBe Some("generic head function")
  }

  test("should correctly calculate result types on correct inputs") {
    extractTypeFunction.apply(List(Typed[String])) shouldBe Typed.fromInstance("OK: String").validNel
    extractTypeFunction.apply(List(Typed[Int])) shouldBe Typed.fromInstance("OK: Int").validNel

    headFunction.apply(List(Typed.genericTypeClass[java.util.List[_]](List(Typed[Int])))) shouldBe Typed[Int].validNel
    val typedList = Typed.genericTypeClass[java.util.List[_]](List(Typed[String]))
    headFunction.apply(List(Typed.genericTypeClass[java.util.List[_]](List(typedList)))) shouldBe typedList.validNel
    val typedMap = TypedObjectTypingResult(List("a" -> Typed[Int], "b" -> Typed[String]))
    headFunction.apply(List(Typed.genericTypeClass[java.util.List[_]](List(typedMap)))) shouldBe typedMap.validNel
  }

  // FIXME: Add expected results to this test.
  ignore("should correctly handle illegal input types") {
    extractTypeFunction.apply(List(Typed[Double])) shouldBe ""
    extractTypeFunction.apply(List(Typed[Map[_, _]])) shouldBe ""
    extractTypeFunction.apply(List()) shouldBe ""
    extractTypeFunction.apply(List(Typed[Int], Typed[Double])) shouldBe ""

    headFunction.apply(List(Typed[Int])) shouldBe ""
    headFunction.apply(List(Typed[Set[_]])) shouldBe ""
    headFunction.apply(List(Typed[Map[_, _]])) shouldBe ""
    headFunction.apply(List()) shouldBe ""
    headFunction.apply(List(Typed[List[_]], Typed[Int])) shouldBe ""
  }
}
