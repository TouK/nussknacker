package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.functor._
import jdk.internal.net.http.common.Log.errors
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo, ProcessCompiler, ProcessValidator}
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.Assertion
import pl.touk.nussknacker.engine.test.testcase.Assertion.PredicateAssertion
import pl.touk.nussknacker.engine.test.testcase.TestCase
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.functions.conversion
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.test.testcase.AssertionCompilationError.PredicateAssertionCompilationError
import pl.touk.nussknacker.ui.process.test.testcase.AssertionValidationError.AssertionConfiguredForNotExistingNodesError
import pl.touk.nussknacker.ui.process.test.testcase.CompiledAssertion.CompiledPredicateAssertion

import java.util.UUID

class AssertionsCompilerSpec
    extends AnyFunSuite
    with Matchers
    with Inside
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.util.Implicits._

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withService("enricher1", Some(Typed[String]), Parameter[String](ParameterName("par1")))
    .withSink("sink")
    .withGlobalVariable("CONV", conversion)
    .build

  private val modelDefinitionWithClasses = ModelDefinitionWithClasses(baseDefinition)

  private val assertionsCompiler: AssertionsCompiler = {
    val globalVariablesPreparer =
      GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)
    val expressionCompiler = ExpressionCompiler.withOptimization(
      getClass.getClassLoader,
      new SimpleDictRegistry(Map.empty),
      modelDefinitionWithClasses.modelDefinition.expressionConfig,
      modelDefinitionWithClasses.classDefinitions,
      ExpressionEvaluator.unOptimizedEvaluator(
        globalVariablesPreparer
      )
    )
    new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)
  }

  private val scenario = ScenarioBuilder
    .streaming("process1")
    .source("id1", "sourceWithUnknown")
    .enricher("enricher1", "enricherOutput", "enricher1", "par1" -> "'abc'".spel)
    .buildSimpleVariable("result-id2", "result", "#input".spel)
    .emptySink("sink1", "sink")

  test("should compile valid assertions for scenario") {
    val test = prepareTestCase(
      Map(
        NodeId("sink1") -> List(
          PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#contexts.size".spel, description = None)
        )
      )
    )

    val compiledAssertions = compileScenarioWithAssertions(scenario, test).validValue

    compiledAssertions.assertions.size shouldBe 1
    compiledAssertions.assertions(NodeId("sink1")).size shouldBe 1
    val compiledAssertion =
      compiledAssertions.assertions(NodeId("sink1")).head.asInstanceOf[CompiledPredicateAssertion]
    compiledAssertion.operator shouldBe Assertion.AssertionOperator.Equals
    compiledAssertion.expectedExpression.original shouldBe "1"
    compiledAssertion.actualExpression.original shouldBe "#contexts.size"
  }

  test("should produce errors for assertions on missing nodes") {
    val test = prepareTestCase(
      Map(
        NodeId("notExistingSink") -> List(
          PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#contexts.size".spel, description = None)
        )
      )
    )

    val errors = compileScenarioWithAssertions(scenario, test).invalidValue

    errors.toList shouldBe List(
      AssertionConfiguredForNotExistingNodesError(
        NonEmptyList.one(NodeId("notExistingSink"))
      )
    )
  }

  test("should produce errors for multiple assertions") {
    val nodeId = NodeId("sink1")
    val assertionWithSyntaxErrorsInBothExpressions =
      PredicateAssertion(Assertion.AssertionOperator.Equals, "'123".spel, "#contexts.size,".spel, description = None)
    val assertionComparingUnrelatedTypes =
      PredicateAssertion(Assertion.AssertionOperator.Equals, "'string'".spel, "123".spel, description = None)
    val test = prepareTestCase(
      Map(
        nodeId -> List(
          assertionWithSyntaxErrorsInBothExpressions,
          assertionComparingUnrelatedTypes,
        )
      )
    )

    val errors = compileScenarioWithAssertions(scenario, test).invalidValue
    errors.size shouldBe 3
    val assertionWithErrorsInBothExpressionsActualExpectedFieldError :: assertionWithErrorsInBothExpressionsActualFieldError :: assertionComparingUnrelatedTypesError :: Nil =
      errors.toList

    inside(assertionWithErrorsInBothExpressionsActualExpectedFieldError) {
      case PredicateAssertionCompilationError(
            NonEmptyList(
              ExpressionParserCompilationError(
                message,
                _,
                Some(ParameterName(parameterName)),
                originalExpr,
                details
              ),
              Nil
            ),
            assertion,
            field,
            _
          ) =>
        message shouldBe "Cannot find terminating ' for string"
        parameterName shouldBe PredicateAssertionCompilationError.Field.Expected.entryName
        field shouldBe PredicateAssertionCompilationError.Field.Expected
        originalExpr shouldBe "'123"
        details shouldBe defined
        assertion shouldBe assertionWithSyntaxErrorsInBothExpressions
    }

    inside(assertionWithErrorsInBothExpressionsActualFieldError) {
      case PredicateAssertionCompilationError(
            NonEmptyList(
              ExpressionParserCompilationError(
                message,
                _,
                Some(ParameterName(parameterName)),
                originalExpr,
                details
              ),
              Nil
            ),
            assertion,
            field,
            _
          ) =>
        message shouldBe "Unexpected text"
        parameterName shouldBe PredicateAssertionCompilationError.Field.Actual.entryName
        field shouldBe PredicateAssertionCompilationError.Field.Actual
        originalExpr shouldBe "#contexts.size,"
        details shouldBe defined
        assertion shouldBe assertionWithSyntaxErrorsInBothExpressions
    }

    inside(assertionComparingUnrelatedTypesError) {
      case PredicateAssertionCompilationError(
            NonEmptyList(
              ExpressionParserCompilationError(
                message,
                `nodeId`,
                Some(ParameterName(parameterName)),
                originalExpr,
                details
              ),
              Nil
            ),
            assertion,
            field,
            `nodeId`
          ) =>
        message shouldBe "Operator '==' used with not comparable types: String and Integer"
        parameterName shouldBe PredicateAssertionCompilationError.Field.Actual.entryName
        field shouldBe PredicateAssertionCompilationError.Field.Actual
        originalExpr shouldBe "'string' == 123"
        details shouldBe None
        assertion shouldBe assertionComparingUnrelatedTypes
    }
  }

  test("should compile assertions with various types used in SpEL") {
    forAll(
      Table[Assertion, ValidatedNel[String, Unit]](
        ("assertion", "compilation result"),
        (
          PredicateAssertion(Assertion.AssertionOperator.Equals, "'abc'".spel, "'def'".spel, description = None),
          Validated.unit,
        ),
        (
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "'abc'".spel,
            "#CONV.toAny(123)".spel,
            description = None
          ),
          Validated.unit,
        ),
        (
          PredicateAssertion(Assertion.AssertionOperator.Equals, "'abc'".spel, "123".spel, description = None),
          Validated.invalidNel("Operator '==' used with not comparable types: String and Integer")
        ),
        (
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "{'abc', 'def'}".spel,
            "{'xyz'}".spel,
            description = None
          ),
          Validated.unit
        ),
        (
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "{'abc', 'def'}".spel,
            "{123}".spel,
            description = None
          ),
          Validated.invalidNel("Operator '==' used with not comparable types: List[String] and List[Integer]")
        ),
        (
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "{'abc', 'def'}".spel,
            "#CONV.toAny({'xyz'})".spel,
            description = None
          ),
          Validated.unit
        ),
      )
    ) { (assertion, expectedCompilationResult) =>
      val nodeId = NodeId("sink1")
      val test = prepareTestCase(
        Map(
          nodeId -> List(assertion)
        )
      )

      val compilationResult = compileScenarioWithAssertions(scenario, test)

      val actualCompilationResult = compilationResult.void
        .leftMap { errors =>
          errors.flatMap {
            case PredicateAssertionCompilationError(compilationErrors, _, _, _) =>
              compilationErrors.map {
                case ExpressionParserCompilationError(message, _, _, _, _) =>
                  message
                case other =>
                  fail(s"Unexpected error: $other")
              }
            case other =>
              fail(s"Unexpected error: $other")
          }
        }

      actualCompilationResult shouldBe expectedCompilationResult
    }
  }

  private def compileScenarioWithAssertions(scenario: CanonicalProcess, test: TestCase) = {
    val typing  = compileScenarioForTyping(scenario)
    val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)

    val assertionCompilationResult = assertionsCompiler.compile(
      test,
      typing,
      jobData
    )
    assertionCompilationResult
  }

  private def compileScenarioForTyping(scenario: CanonicalProcess): Map[String, NodeTypingData] = {
    val jobData: JobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)

    val compilationResult = ProcessValidator
      .default(
        modelDefinitionWithClasses,
        new SimpleDictRegistry(Map.empty),
        CustomProcessValidatorLoader.emptyCustomProcessValidator,
      )
      .asInstanceOf[ProcessCompiler]
      .compile(scenario)

    compilationResult.result match {
      case Validated.Valid(_) => compilationResult.typing.mapValuesNow(nodeInfoToResult).toMap
      case Validated.Invalid(errors) =>
        throw new IllegalStateException(s"Process compilation ended with errors: $errors")
    }
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo) = NodeTypingData(
    typingInfo.inputValidationContext.localVariables,
    typingInfo.parameters.map(_.map(DefinitionsService.createUIParameter)),
    typingInfo.expressionsTypingInfo
  )

  private def prepareTestCase(assertions: Map[NodeId, List[Assertion]]): TestCase = {
    TestCase(
      id = UUID.randomUUID(),
      name = "dummy",
      inputs = "dummy",
      mocks = Map.empty,
      assertions = assertions
    )
  }

}
