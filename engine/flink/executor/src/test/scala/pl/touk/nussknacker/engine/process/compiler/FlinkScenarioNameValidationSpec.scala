package pl.touk.nussknacker.engine.process.compiler

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration, ProcessConfigCreator}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord
import pl.touk.nussknacker.engine.testing.LocalModelData

class FlinkScenarioNameValidationSpec extends AnyFunSuite with Matchers with ProcessTestHelpers with Inside {

  private val processDefinition = ProcessDefinition[ObjectDefinition](
    services = Map(),
    sourceFactories = Map("source" -> ObjectDefinition(List.empty, Typed[SimpleRecord])),
    sinkFactories = Map("sink" -> ObjectDefinition.noParam),
    customStreamTransformers = Map(),
    signalsWithTransformers = Map(),
    expressionConfig = ExpressionDefinition(Map.empty, List.empty, List.empty, LanguageConfiguration.default, optimizeCompilation = false, strictTypeChecking = true,
      Map.empty, hideMetaVariable = false, strictMethodsChecking = true, staticMethodInvocationsChecking = false,
      methodExecutionForUnknownAllowed = false, dynamicPropertyAccessAllowed = false, spelExpressionExcludeList = SpelExpressionExcludeList.default,
      customConversionsProviders = List.empty),
    settings = ClassExtractionSettings.Default
  )

  test("we are checking name validation") {
    val processWithInvalidName =
      ScenarioBuilder
        .streaming("invalid+name")
        .source("sourceId", "source")
        .emptySink("emptySink", "sink")
        .toCanonicalProcess

    val creator: ProcessConfigCreator = ProcessTestHelpers.prepareCreator(Nil, ConfigFactory.empty())
    val modelData = LocalModelData(config, creator)
    val processValidator = modelData.prepareValidatorForCategory(None)

    val validationResult = processValidator
      .validate(processWithInvalidName)

    validationResult.result should matchPattern {
      case Invalid(NonEmptyList(ScenarioNameValidationError("invalid+name", _), _)) =>
    }
  }

}
