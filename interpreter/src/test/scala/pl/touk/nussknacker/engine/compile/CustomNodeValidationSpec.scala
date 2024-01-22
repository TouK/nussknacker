package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.validationHelpers._
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.MetaVariables
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, spel}

import scala.collection.Set

class CustomNodeValidationSpec extends AnyFunSuite with Matchers with OptionValues {

  import spel.Implicits._

  private val components = List(
    ComponentDefinition("myCustomStreamTransformer", SimpleStreamTransformer),
    ComponentDefinition("addingVariableStreamTransformer", AddingVariableStreamTransformer),
    ComponentDefinition("clearingContextStreamTransformer", ClearingContextStreamTransformer),
    ComponentDefinition("producingTupleTransformer", ProducingTupleTransformer),
    ComponentDefinition("unionTransformer", UnionTransformer),
    ComponentDefinition("unionTransformerWithMainBranch", UnionTransformerWithMainBranch),
    ComponentDefinition("nonEndingCustomNodeReturningTransformation", NonEndingCustomNodeReturningTransformation),
    ComponentDefinition("nonEndingCustomNodeReturningUnit", NonEndingCustomNodeReturningUnit),
    ComponentDefinition("addingVariableOptionalEndingCustomNode", AddingVariableOptionalEndingStreamTransformer),
    ComponentDefinition("optionalEndingTransformer", OptionalEndingStreamTransformer),
    ComponentDefinition("noBranchParameters", DynamicNoBranchParameterJoinTransformer),
    ComponentDefinition("mySource", SimpleStringSource),
    ComponentDefinition("dummySink", SinkFactory.noParam(new Sink {})),
    ComponentDefinition("stringService", SimpleStringService),
    ComponentDefinition("enricher", Enricher)
  )

  private val processBase = ScenarioBuilder.streaming("proc1").source("sourceId", "mySource")

  private val modelDefinition = ModelDefinition(
    ComponentDefinitionWithImplementation
      .forList(components, ComponentsUiConfig.Empty, info => ComponentId(info.toString), Map.empty),
    ModelDefinitionBuilder.emptyExpressionConfig,
    ClassExtractionSettings.Default
  )

  private val validator = ProcessValidator.default(
    ModelDefinitionWithClasses(modelDefinition),
    new SimpleDictRegistry(Map.empty),
    CustomProcessValidatorLoader.emptyCustomProcessValidator
  )

  private def validate(process: CanonicalProcess) = validator.validate(process, isFragment = false)

  test("valid scenario") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#additionalVar1")
      .emptySink("out", "dummySink")

    val validationResult = validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta"  -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("out").value shouldBe Map(
      "input"     -> Typed[String],
      "outPutVar" -> Unknown,
      "meta"      -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("valid scenario - custom node with optional end and with output var as ending node") {
    val validProcess = processBase
      .endingCustomNode(
        "custom1",
        Some("outputVar"),
        "addingVariableOptionalEndingCustomNode",
        "stringVal" -> "'someValue'"
      )

    val validationResult = validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta"  -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("valid scenario - custom node with optional end and without output var as ending node") {
    val validProcess = processBase
      .endingCustomNode("custom1", None, "optionalEndingTransformer", "stringVal" -> "'someValue'")

    val validationResult = validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta"  -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("valid scenario - custom node with optional end with ongoing node") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "optionalEndingTransformer", "stringVal" -> "'someValue'")
      .emptySink("out", "dummySink")

    val validationResult = validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta"  -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("out").value shouldBe Map(
      "input"     -> Typed[String],
      "outPutVar" -> Unknown,
      "meta"      -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("invalid scenario - non ending custom node ends scenario - transformation api case") {
    val invalidProcess = processBase
      .endingCustomNode(
        "custom1",
        Some("outputVar"),
        "nonEndingCustomNodeReturningTransformation",
        "stringVal" -> "'someValue'"
      )

    validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(InvalidTailOfBranch("custom1"), _)) =>
    }
  }

  test("invalid scenario - non ending custom node ends scenario - non-transformation api case") {
    val invalidProcess = processBase
      .endingCustomNode("custom1", Some("outputVar"), "nonEndingCustomNodeReturningUnit", "stringVal" -> "'someValue'")

    validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(InvalidTailOfBranch("custom1"), _)) =>
    }
  }

  test("invalid scenario with non-existing variable") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#nonExisitngVar")
      .emptySink("out", "dummySink")

    validate(invalidProcess).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'nonExisitngVar'",
                "custom1",
                Some("stringVal"),
                "#nonExisitngVar"
              ),
              _
            )
          ) =>
    }
  }

  test("invalid scenario with variable of a incorrect type") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "42")
      .emptySink("out", "dummySink")

    val expectedMsg = s"Bad expression type, expected: String, found: ${Typed.fromInstance(42).display}"
    validate(invalidProcess).result should matchPattern {
      case Invalid(
            NonEmptyList(ExpressionParserCompilationError(`expectedMsg`, "custom1", Some("stringVal"), "42"), _)
          ) =>
    }
  }

  test("valid scenario using context transformation api - adding variable") {
    val outputVarName = "outPutVar"
    val endNodeId     = "end"
    val validProcess = processBase
      .customNode("custom1", outputVarName, "addingVariableStreamTransformer")
      .buildSimpleVariable("out", "out", "#outPutVar")
      .emptySink(endNodeId, "dummySink")

    val compilationResult = validate(validProcess)
    compilationResult.result should matchPattern { case Valid(_) =>
    }
    compilationResult.typing(endNodeId).inputValidationContext.get(outputVarName).value shouldEqual Typed[String]
  }

  test("invalid scenario using context transformation api - adding variable without specifying it's name") {
    val missingOutputVarProcess = processBase
      .customNodeNoOutput("custom1", "addingVariableStreamTransformer")
      .emptySink("out", "dummySink")

    val missingOutValidationResult = validate(missingOutputVarProcess).result
    missingOutValidationResult.isValid shouldBe false
    val missingOutErrors = missingOutValidationResult.swap.toOption.value.toList
    missingOutErrors should have size 1
    missingOutErrors.head should matchPattern {
      case MissingParameters(params, _) if params == Set("OutputVariable") =>
    }
  }

  test("valid scenario using context transformation api - clearing context") {
    val redundantOutputVarProcess = processBase
      .customNodeNoOutput("custom1", "clearingContextStreamTransformer")
      .buildSimpleVariable("out", "out", "#input")
      .emptySink("end", "dummySink")

    val redundantOutValidationResult = validate(redundantOutputVarProcess).result
    redundantOutValidationResult.isValid shouldBe false
    val redundantOutErrors = redundantOutValidationResult.swap.toOption.value.toList
    redundantOutErrors should have size 1
    redundantOutErrors.head should matchPattern {
      case ExpressionParserCompilationError(message, _, _, _) if message.startsWith("Unresolved reference 'input'") =>
    }
  }

  test("valid scenario using context transformation api - adding variable using compile time evaluated parameter") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "producingTupleTransformer", "numberOfFields" -> "1 + 1")
      .buildSimpleVariable("out", "result", "#outPutVar.field1")
      .emptySink("end", "dummySink")

    val validationResult = validate(validProcess).result
    validationResult should matchPattern { case Valid(_) =>
    }

    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "producingTupleTransformer", "numberOfFields" -> "1 + 1")
      .buildSimpleVariable("out", "result", "#outPutVar.field22")
      .emptySink("end", "dummySink")

    val validationResult2 = validate(invalidProcess).result
    validationResult2.isValid shouldBe false
    val errors = validationResult2.swap.toOption.value.toList
    errors should have size 1
    errors.head should matchPattern {
      case ExpressionParserCompilationError(message, _, _, _)
          if message.startsWith("There is no property 'field22' in type: Record{field1: String, field2: String}") =>
    }
  }

  test("valid scenario using context transformation api - union") {
    val validProcess = processWithUnion("#outPutVar.branch1")

    val validationResult = validate(validProcess)
    validationResult.result should matchPattern { case Valid(_) =>
    }
    validationResult.variablesInNodes("stringService")("outPutVar") shouldBe TypedObjectTypingResult(
      Map("branch2" -> Typed[Int], "branch1" -> Typed[String])
    )
  }

  test("invalid scenario using context transformation api - union") {
    val invalidProcess    = processWithUnion("#outPutVar.branch2")
    val validationResult2 = validate(invalidProcess).result

    val errors = validationResult2.swap.toOption.value.toList
    errors should have size 1
    errors.head should matchPattern {
      case ExpressionParserCompilationError(
            "Bad expression type, expected: String, found: Integer",
            "stringService",
            Some("stringParam"),
            _
          ) =>
    }
  }

  test("global variables in scope after custom context transformation") {
    val process = processWithUnion("#meta.processName")

    val validationResult = validate(process).result
    validationResult should matchPattern { case Valid(_) =>
    }
  }

  test("validate nodes after union if validation of part before fails") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "mySource")
          .filter("invalidFilter", "not.a.valid.expression")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .join(
            "join1",
            "unionTransformer",
            Some("outPutVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "#input")
            )
          )
          .processorEnd("stringService", "stringService", "stringParam" -> "''")
      )
    val validationResult = validate(process)

    validationResult.variablesInNodes("stringService")("outPutVar") shouldBe TypedObjectTypingResult(
      Map("branch1" -> Typed[String])
    )
    val errors = validationResult.result.swap.toList.flatMap(_.toList).map(_.nodeIds)
    errors shouldBe List(Set("invalidFilter"))

  }

  private def processWithUnion(serviceExpression: String) =
    ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "mySource")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join(
            "join1",
            "unionTransformer",
            Some("outPutVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "#input"),
              "branch2" -> List("key" -> "'key2'", "value" -> "#input.length()")
            )
          )
          .processorEnd("stringService", "stringService", "stringParam" -> serviceExpression)
      )

  test("extract expression typing info from join") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .sources(
          GraphBuilder
            .source("sourceId1", "mySource")
            .branchEnd("branch1", "join1"),
          GraphBuilder
            .source("sourceId2", "mySource")
            .branchEnd("branch2", "join1"),
          GraphBuilder
            .join(
              "join1",
              "unionTransformer",
              Some("outPutVar"),
              List(
                "branch1" -> List("key" -> "'key1'", "value" -> "'ala'"),
                "branch2" -> List("key" -> "'key2'", "value" -> "123")
              )
            )
            .processorEnd("stringService", "stringService", "stringParam" -> "'123'")
        )

    val validationResult = validate(process)
    validationResult.result should matchPattern { case Valid(_) =>
    }

    validationResult.expressionsInNodes shouldEqual Map(
      "sourceId1"           -> Map.empty,
      "$edge-branch1-join1" -> Map.empty,
      "sourceId2"           -> Map.empty,
      "$edge-branch2-join1" -> Map.empty,
      "join1" -> Map(
        "key-branch1" -> SpelExpressionTypingInfo(
          Map(PositionRange(0, 6) -> Typed.fromInstance("key1")),
          Typed.fromInstance("key1")
        ),
        "key-branch2" -> SpelExpressionTypingInfo(
          Map(PositionRange(0, 6) -> Typed.fromInstance("key2")),
          Typed.fromInstance("key2")
        ),
        "value-branch1" -> SpelExpressionTypingInfo(
          Map(PositionRange(0, 5) -> Typed.fromInstance("ala")),
          Typed.fromInstance("ala")
        ),
        "value-branch2" -> SpelExpressionTypingInfo(
          Map(PositionRange(0, 3) -> Typed.fromInstance(123)),
          Typed.fromInstance(123)
        )
      ),
      "stringService" -> Map(
        "stringParam" -> SpelExpressionTypingInfo(
          Map(PositionRange(0, 5) -> Typed.fromInstance("123")),
          Typed.fromInstance("123")
        )
      )
    )
  }

  test("validation of types of branch parameters") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .sources(
          GraphBuilder
            .source("sourceId1", "mySource")
            .branchEnd("branch1", "join1"),
          GraphBuilder
            .source("sourceId2", "mySource")
            .branchEnd("branch2", "join1"),
          GraphBuilder
            .join(
              "join1",
              "unionTransformer",
              Some("outPutVar"),
              List(
                "branch1" -> List("key" -> "'key1'", "value" -> "'ala'"),
                "branch2" -> List("key" -> "123", "value" -> "123")
              )
            )
            .processorEnd("stringService", "stringService", "stringParam" -> "'123'")
        )

    val validationResult = validate(process)
    val expectedMsg      = s"Bad expression type, expected: CharSequence, found: ${Typed.fromInstance(123).display}"
    validationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(`expectedMsg`, "join1", Some("key for branch branch2"), "123"),
              Nil
            )
          ) =>
    }
  }

  test("custom validation of branch parameters") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .sources(
          GraphBuilder
            .source("sourceId1", "mySource")
            .branchEnd("branch1", "join1"),
          GraphBuilder
            .source("sourceId2", "mySource")
            .branchEnd("branch2", "join1"),
          GraphBuilder
            .join(
              "join1",
              "unionTransformer",
              Some("outPutVar"),
              List(
                "branch1" -> List("key" -> "''", "value" -> "'ala'"),
                "branch2" -> List("key" -> "'key2'", "value" -> "null")
              )
            )
            .processorEnd("stringService", "stringService", "stringParam" -> "'123'")
        )

    val validationResult = validate(process)
    validationResult.result shouldBe Invalid(
      NonEmptyList(
        BlankParameter(
          "This field value is required and can not be blank",
          "Please fill field value for this parameter",
          "key for branch branch1",
          "join1"
        ),
        Nil
      )
    )
  }

  test("scenario with enricher") {
    val validProcess = processBase
      .enricher("enricher", "outPutVar", "enricher")
      .emptySink("out", "dummySink")

    val validationResult = validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("enricher").value shouldBe Map(
      "input" -> Typed[String],
      "meta"  -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("out").value shouldBe Map(
      "input"     -> Typed[String],
      "outPutVar" -> Typed[String],
      "meta"      -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("join-custom-join should work (branch end is in different part of scenario)") {
    val validProcess =
      ScenarioBuilder
        .streaming("proc1")
        .sources(
          GraphBuilder
            .source("sourceId1", "mySource")
            .branchEnd("branch1", "join1"),
          GraphBuilder
            .join(
              "join1",
              "unionTransformer",
              Some("outPutVar"),
              List("branch1" -> List("key" -> "'key1'", "value" -> "'ala'"))
            )
            .customNode("custom1", "outPutVar3", "producingTupleTransformer", "numberOfFields" -> "2")
            .branchEnd("branch2", "join2"),
          GraphBuilder
            .join(
              "join2",
              "unionTransformer",
              Some("outPutVar2"),
              List("branch2" -> List("key" -> "'key1'", "value" -> "'ala'"))
            )
            .processorEnd("stringService", "stringService", "stringParam" -> "'123'")
        )
    val validationResult = validate(validProcess)

    validationResult.result shouldBe Symbol("valid")
  }

  test("eager params in joins") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .sources(
          GraphBuilder
            .source("sourceId1", "mySource")
            .branchEnd("branch1", "join1"),
          GraphBuilder
            .source("sourceId2", "mySource")
            .branchEnd("branch2", "join1"),
          GraphBuilder
            .join(
              "join1",
              "unionTransformerWithMainBranch",
              Some("outPutVar"),
              List(
                "branch1" -> List("key" -> "'key1'", "value" -> "'ala'", "mainBranch" -> "true"),
                "branch2" -> List("key" -> "'key2'", "value" -> "123", "mainBranch" -> "false")
              )
            )
            .emptySink("sink", "dummySink")
        )

    val validationResult = validate(process)
    validationResult.result should matchPattern { case Valid(_) =>
    }
  }

  test("validate union using variables in branches with custom nodes") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "mySource")
          .buildSimpleVariable("variable1", "variable1", "42")
          .customNode("custom", "unusedVariable", "addingVariableStreamTransformer")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .customNode("custom2", "unusedVariable2", "addingVariableStreamTransformer")
          .customNode("custom3", "unusedVariable3", "addingVariableStreamTransformer")
          .buildSimpleVariable("variable2", "variable2", "42")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join(
            "join1",
            "unionTransformer",
            Some("unionVariable"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "#variable1"),
              "branch2" -> List("key" -> "'key2'", "value" -> "#variable2")
            )
          )
          .processorEnd("stringService", "stringService", "stringParam" -> "''")
      )

    val validationResult = validate(process)

    validationResult.result.isValid shouldBe true
  }

  test("should validate branch contexts without branch parameters") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "mySource")
          .buildSimpleVariable("var1", "intVal", "123")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .buildSimpleVariable("var2", "strVal", "'abc'")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join("join1", "noBranchParameters", None, List())
          .emptySink("end", "dummySink")
      )
    val validationResult = validate(process)

    validationResult.result shouldBe Validated
      .invalid(CustomNodeError("join1", "Validation contexts do not match", Option.empty))
      .toValidatedNel
  }

}
