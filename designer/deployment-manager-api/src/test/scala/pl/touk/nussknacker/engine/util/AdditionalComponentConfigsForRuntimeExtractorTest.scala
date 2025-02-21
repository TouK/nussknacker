package pl.touk.nussknacker.engine.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentGroupName,
  DesignerWideComponentId,
  ParameterAdditionalUIConfig
}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithDictEditor}
import pl.touk.nussknacker.engine.util.AdditionalComponentConfigsForRuntimeExtractorTest.{
  componentConfigWithDictionaryEditorInParameter,
  componentConfigWithOnlyDictEditorParameters,
  componentConfigWithoutDictionaryEditorInParameter
}

class AdditionalComponentConfigsForRuntimeExtractorTest extends AnyFunSuite with Matchers {

  test("should filter only components and parameters with dictionary editors") {
    val additionalConfig = Map(
      DesignerWideComponentId("componentA") -> componentConfigWithDictionaryEditorInParameter,
      DesignerWideComponentId("componentB") -> componentConfigWithoutDictionaryEditorInParameter,
    )
    val filteredResult =
      AdditionalComponentConfigsForRuntimeExtractor.getRequiredAdditionalConfigsForRuntime(additionalConfig)

    filteredResult shouldBe Map(
      DesignerWideComponentId("componentA") -> componentConfigWithOnlyDictEditorParameters
    )
  }

}

object AdditionalComponentConfigsForRuntimeExtractorTest {

  private val parameterAWithDictEditor = (
    ParameterName("parameterA"),
    ParameterAdditionalUIConfig(
      required = true,
      initialValue = Some(FixedExpressionValue("'someInitialValueExpression'", "someInitialValueLabel")),
      hintText = None,
      valueEditor = Some(ValueInputWithDictEditor("someDictA", allowOtherValue = true)),
      valueCompileTimeValidation = None
    )
  )

  private val parameterBWithDictEditor = (
    ParameterName("parameterB"),
    ParameterAdditionalUIConfig(
      required = false,
      initialValue = None,
      hintText = Some("someHint"),
      valueEditor = Some(ValueInputWithDictEditor("someDictB", allowOtherValue = false)),
      valueCompileTimeValidation = None
    )
  )

  private val parameterWithoutDictEditor = (
    ParameterName("parameterC"),
    ParameterAdditionalUIConfig(
      required = true,
      initialValue = None,
      hintText = None,
      valueEditor = None,
      valueCompileTimeValidation = None
    )
  )

  private val componentConfigWithDictionaryEditorInParameter = ComponentAdditionalConfig(
    parameterConfigs = Map(
      parameterAWithDictEditor,
      parameterBWithDictEditor,
      parameterWithoutDictEditor
    ),
    icon = Some("someIcon"),
    docsUrl = Some("someDocUrl"),
    componentGroup = Some(ComponentGroupName("Service"))
  )

  private val componentConfigWithoutDictionaryEditorInParameter = ComponentAdditionalConfig(
    parameterConfigs = Map(parameterWithoutDictEditor),
    icon = Some("someOtherIcon"),
    docsUrl = Some("someOtherDocUrl"),
    componentGroup = Some(ComponentGroupName("Service"))
  )

  private val componentConfigWithOnlyDictEditorParameters = ComponentAdditionalConfig(
    parameterConfigs = Map(
      parameterAWithDictEditor,
      parameterBWithDictEditor
    ),
    icon = Some("someIcon"),
    docsUrl = Some("someDocUrl"),
    componentGroup = Some(ComponentGroupName("Service"))
  )

}
