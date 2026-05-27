package pl.touk.nussknacker.engine.definition.component.parameter

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.{HintText, Label, ParamName}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.parameter.ParameterName

import scala.annotation.unused

class ParameterExtractorTest extends AnyFunSuite with Matchers {

  test("should extract hintText from @HintText annotation") {
    val param = extractParam("withHintText")

    param.hintText shouldBe Some("some hint")
  }

  test("should return no hintText when @HintText annotation is not present") {
    val param = extractParam("withoutHintText")

    param.hintText shouldBe None
  }

  test("should prefer hintText from config over @HintText annotation") {
    val parameterConfig = ParameterConfig.empty.copy(hintText = Some("from config"))
    val param           = extractParam("withHintText", parameterConfig)

    param.hintText shouldBe Some("from config")
  }

  test("should extract labelOpt from @Label annotation") {
    val param = extractParam("withLabelText")

    param.labelOpt shouldBe Some("some label")
  }

  test("should return no labelOpt when @Label annotation is not present") {
    val param = extractParam("withoutHintText")

    param.labelOpt shouldBe None
  }

  test("should prefer label from config over @Label annotation") {
    val parameterConfig = ParameterConfig.empty.copy(label = Some("from config"))
    val param           = extractParam("withLabelText", parameterConfig)

    param.labelOpt shouldBe Some("from config")
  }

  @unused private def withHintText(@ParamName("param") @HintText("some hint") param: String): Unit = ()
  @unused private def withoutHintText(@ParamName("param") param: String): Unit                     = ()
  @unused private def withLabelText(@ParamName("param") @Label("some label") param: String): Unit  = ()

  private def extractParam(
      methodName: String,
      parameterConfig: ParameterConfig = ParameterConfig.empty
  ) = {
    val reflectParam = this.getClass.getDeclaredMethod(methodName, classOf[String]).getParameters.apply(0)
    ParameterExtractor.extractParameter(
      reflectParam,
      Map(ParameterName("param") -> parameterConfig),
      GlobalParametersConfig.default
    )
  }

}
