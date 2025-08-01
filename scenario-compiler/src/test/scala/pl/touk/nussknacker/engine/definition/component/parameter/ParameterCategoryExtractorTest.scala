package pl.touk.nussknacker.engine.definition.component.parameter

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{
  ParameterCategory => ApiParameterCategory,
  ParameterCategoryType => ApiParameterCategoryType
}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.ParameterCategory
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class ParameterCategoryExtractorTest extends AnyFunSuite with OptionValues with Matchers {

  test("should take category from config") {
    val parameterConfig = ParameterConfig.empty.copy(category = Some(ParameterCategory.Advanced))
    val parametersData  = getFirstParam("parmNotAnnotated", classOf[String])

    ParameterCategoryExtractor.extract(parametersData, parameterConfig).value shouldBe ParameterCategory.Advanced
  }

  test("should take section from annotation if it is not present in config") {
    val parameterConfig = ParameterConfig.empty
    val parametersData  = getFirstParam("parmAnnotated", classOf[String])

    ParameterCategoryExtractor.extract(parametersData, parameterConfig).value shouldBe ParameterCategory.Advanced
  }

  test("should return none if section is not present") {
    val parameterConfig = ParameterConfig.empty
    val parametersData  = getFirstParam("parmNotAnnotated", classOf[String])

    ParameterCategoryExtractor.extract(parametersData, parameterConfig) shouldBe None
  }

  private def parmNotAnnotated(param: String)                                                                = ()
  private def parmAnnotated(@ApiParameterCategory(`type` = ApiParameterCategoryType.ADVANCED) param: String) = ()

  private def getFirstParam(name: String, params: Class[_]*): ParameterData = {
    val parameter = this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
    ParameterData(parameter, Typed.typedClass(parameter.getType), isLazyParameter = false)
  }

}
