package pl.touk.nussknacker.engine.api.process

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.{CustomParameterValidatorDelegate, NotBlankParameterValidator, StringParameterEditor, TextareaParameterEditor}

class SingleNodeConfigTest extends FunSuite with Matchers {

  test("should combine node configs") {
    val c1 = SingleNodeConfig.zero.copy(params = Some(Map(
      "a" -> ParameterConfig.empty.copy(defaultValue = Some("default"), validators = Some(List(NotBlankParameterValidator)), editor = Some(TextareaParameterEditor)),
      "b" -> ParameterConfig.empty.copy(validators = Some(List(NotBlankParameterValidator, CustomParameterValidatorDelegate("custom"))))
    )))

    val c2 = SingleNodeConfig.zero.copy(params = Some(Map(
      "a" -> ParameterConfig.empty.copy(defaultValue = Some("to ignore"), validators = Some(List(CustomParameterValidatorDelegate("custom"))),
        editor = Some(StringParameterEditor)),
      "c" -> ParameterConfig.empty.copy(defaultValue = Some("default value"))
    )))

    import cats.syntax.semigroup._

    c1 |+| c2 shouldBe SingleNodeConfig.zero.copy(params = Some(Map(
      "a" -> ParameterConfig.empty.copy(defaultValue = Some("default"), validators = Some(List(NotBlankParameterValidator, CustomParameterValidatorDelegate("custom"))), editor = Some(TextareaParameterEditor)),
      "b" -> ParameterConfig.empty.copy(validators = Some(List(NotBlankParameterValidator, CustomParameterValidatorDelegate("custom")))),
      "c" -> ParameterConfig.empty.copy(defaultValue = Some("default value"))
    )))
  }
}
