package pl.touk.nussknacker.engine.definition.component.parameter

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{
  ParameterSection => ApiParameterSection,
  ParameterSectionType => ApiParameterSectionType
}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.ParameterSection
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class ParameterSectionExtractorTest extends AnyFunSuite with OptionValues with Matchers {

  test("should take section from config") {
    val parameterConfig = ParameterConfig.empty.copy(section = Some(ParameterSection.Additional))
    val parametersData  = getFirstParam("parmNotAnnotated", classOf[String])

    ParameterSectionExtractor.extract(parametersData, parameterConfig).value shouldBe ParameterSection.Additional
  }

  test("should take section from annotation if it is not present in config") {
    val parameterConfig = ParameterConfig.empty
    val parametersData  = getFirstParam("parmAnnotated", classOf[String])

    ParameterSectionExtractor.extract(parametersData, parameterConfig).value shouldBe ParameterSection.Additional
  }

  test("should return none if section is not present") {
    val parameterConfig = ParameterConfig.empty
    val parametersData  = getFirstParam("parmNotAnnotated", classOf[String])

    ParameterSectionExtractor.extract(parametersData, parameterConfig) shouldBe None
  }

  private def parmNotAnnotated(param: String)                                                                = ()
  private def parmAnnotated(@ApiParameterSection(`type` = ApiParameterSectionType.ADDITIONAL) param: String) = ()

  private def getFirstParam(name: String, params: Class[_]*): ParameterData = {
    val parameter = this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
    ParameterData(parameter, Typed.typedClass(parameter.getType))
  }

}
