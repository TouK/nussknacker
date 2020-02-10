package pl.touk.nussknacker.ui.definition.editor

import org.scalatest._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class ParameterEditorExtractorChainTest extends FlatSpec with Matchers {
  behavior of "ParameterEditorExtractorChain"

  private val fixedValuesEditor = FixedValuesParameterEditor(possibleValues = List(FixedExpressionValue("a", "a")))
  private val stringEditor = StringParameterEditor

  it should "evaluate editor by config" in {
    val param = new Parameter("param", Typed[String], classOf[String], Some(stringEditor))
    val config = ParameterConfig(None, Some(fixedValuesEditor), None)

    val extractor = ParameterEditorExtractorChain(config)

    extractor.evaluateEditor(param) shouldBe fixedValuesEditor
  }

  it should "evaluate editor by param" in {
    val param = new Parameter("param", Typed[String], classOf[String], Some(stringEditor))
    val config = ParameterConfig.empty

    val extractor = ParameterEditorExtractorChain(config)

    extractor.evaluateEditor(param) shouldBe stringEditor
  }

  it should "evaluate editor by type enum" in {
    val param = Parameter[JavaSampleEnum]("param")
    val config = ParameterConfig.empty

    val extractor = ParameterEditorExtractorChain(config)

    extractor.evaluateEditor(param) shouldBe FixedValuesParameterEditor(List(
      FixedExpressionValue(s"T(${classOf[JavaSampleEnum].getName}).${JavaSampleEnum.FIRST_VALUE.name()}", "first_value"),
      FixedExpressionValue(s"T(${classOf[JavaSampleEnum].getName}).${JavaSampleEnum.SECOND_VALUE.name()}", "second_value")
    ))
  }

  it should "evaluate editor by type String" in {
    val param = Parameter[String]("param")
    val config = ParameterConfig.empty

    val extractor = ParameterEditorExtractorChain(config)

    extractor.evaluateEditor(param) shouldBe DualParameterEditor(
      simpleEditor = StringParameterEditor,
      defaultMode = DualEditorMode.RAW
    )
  }

  it should "evaluate default editor" in {
    val param = Parameter[BigDecimal]("param")
    val config = ParameterConfig.empty

    val extractor = ParameterEditorExtractorChain(config)

    extractor.evaluateEditor(param) shouldBe RawParameterEditor
  }
}
