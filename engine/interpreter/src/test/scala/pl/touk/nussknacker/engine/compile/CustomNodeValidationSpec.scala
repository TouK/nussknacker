package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ExpressionParseError, FatalUnknownError, InvalidTailOfBranch, MissingParameters, NodeId}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.compile.NodeTypingInfo._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.variables.MetaVariables
import pl.touk.nussknacker.engine.{api, spel}

import scala.collection.Set
import scala.concurrent.Future


class CustomNodeValidationSpec extends FunSuite with Matchers with OptionValues {

  import spel.Implicits._

  class MyProcessConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "myCustomStreamTransformer" -> WithCategories(SimpleStreamTransformer),
      "addingVariableStreamTransformer" -> WithCategories(AddingVariableStreamTransformer),
      "clearingContextStreamTransformer" -> WithCategories(ClearingContextStreamTransformer),
      "producingTupleTransformer" -> WithCategories(ProducingTupleTransformer),
      "unionTransformer" -> WithCategories(UnionTransformer),
      "unionTransformerWithMainBranch" -> WithCategories(UnionTransformerWithMainBranch),
      "nonEndingCustomNodeReturningTransformation" -> WithCategories(NonEndingCustomNodeReturningTransformation),
      "nonEndingCustomNodeReturningUnit" -> WithCategories(NonEndingCustomNodeReturningUnit),
      "addingVariableOptionalEndingCustomNode" -> WithCategories(AddingVariableOptionalEndingStreamTransformer),
      "optionalEndingTransformer" -> WithCategories(OptionalEndingStreamTransformer)
    )

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
      "mySource" -> WithCategories(SimpleStringSource))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
      "dummySink" -> WithCategories(SinkFactory.noParam(new Sink {
        override def testDataOutput = None
      })))

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
      "stringService" -> WithCategories(SimpleStringService),
      "enricher" -> WithCategories(Enricher)
    )
  }

  object SimpleStringSource extends SourceFactory[String] {
    override def clazz: Class[_] = classOf[String]

    @MethodToInvoke
    def create(): api.process.Source[String] = null
  }

  object SimpleStreamTransformer extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("stringVal")
                @AdditionalVariables(value = Array(new AdditionalVariable(name = "additionalVar1", clazz = classOf[String])))
                stringVal: String) = {}
  }

  object SimpleStringService extends Service {
    @MethodToInvoke
    def invoke(@ParamName("stringParam") param: String): Future[Unit] = ???
  }

  object Enricher extends Service {
    @MethodToInvoke
    def invoke(): Future[String] = ???
  }

  object AddingVariableStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@OutputVariableName variableName: String)(implicit nodeId: NodeId) = {
      ContextTransformation
        .definedBy(_.withVariable(variableName, Typed[String]))
        .implementedBy(null)
    }

  }

  object ClearingContextStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute() = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .implementedBy(null)
    }

  }

  object ProducingTupleTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("numberOfFields") numberOfFields: Int,
                @OutputVariableName variableName: String)
               (implicit nodeId: NodeId): ContextTransformation = {
      ContextTransformation
        .definedBy { context =>
          val newType = TypedObjectTypingResult((1 to numberOfFields).map { i =>
            s"field$i" -> Typed[String]
          }.toMap)
          context.withVariable(variableName, newType)
        }
        .implementedBy(null)
    }

  }

  object UnionTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[_]], // key is only for runtime purpose
                @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
                @OutputVariableName variableName: String): JoinContextTransformation = {
      ContextTransformation
        .join
        .definedBy { contexts =>
          val newType = TypedObjectTypingResult(contexts.toSeq.map {
            case (branchId, _) =>
              branchId -> valueByBranchId(branchId).returnType
          }.toMap)
          Valid(ValidationContext(Map(variableName -> newType)))
        }
        .implementedBy(null)
    }

  }

  object UnionTransformerWithMainBranch extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[_]], // key is only for runtime purpose
                @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
                @BranchParamName("mainBranch") mainBranch: Map[String, Boolean],
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation
        .join
        .definedBy { contexts =>
          val (mainBranches, joinedBranches) = contexts.partition {
            case (branchId, _) => mainBranch(branchId)
          }
          if (mainBranches.size != 1) {
            Invalid(FatalUnknownError("Should be exact one main branch")).toValidatedNel
          } else {
            val mainBranchContext = mainBranches.head._2

            val newType = TypedObjectTypingResult(joinedBranches.toSeq.map {
              case (branchId, _) =>
                branchId -> valueByBranchId(branchId).returnType
            }.toMap)

            mainBranchContext.withVariable(variableName, newType)
          }
        }
        .implementedBy(null)
    }

  }

  object NonEndingCustomNodeReturningTransformation extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): ContextTransformation = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .implementedBy(null)      
    }

    override def canBeEnding: Boolean = false
  }
  
  object NonEndingCustomNodeReturningUnit extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): Unit = {
    }

    override def canBeEnding: Boolean = false
  }

  object OptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): Unit = {
    }

    override def canBeEnding: Boolean = true
  }

  object AddingVariableOptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String,
                @OutputVariableName variableName: String): Unit = {
    }

    override def canBeEnding: Boolean = true
  }

  private val processBase = EspProcessBuilder.id("proc1").exceptionHandler().source("sourceId", "mySource")
  private val objectWithMethodDef = ProcessDefinitionExtractor.extractObjectWithMethods(new MyProcessConfigCreator,
    process.ProcessObjectDependencies(ConfigFactory.empty, DefaultObjectNaming))
  private val validator = ProcessValidator.default(objectWithMethodDef, new SimpleDictRegistry(Map.empty))

  test("valid process") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#additionalVar1")
      .sink("out", "''", "dummySink")

    val validationResult = validator.validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("out").value shouldBe Map(
      "input" -> Typed[String],
      "outPutVar" -> Unknown,
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("valid process - custom node with optional end and with output var as ending node") {
    val validProcess = processBase
      .endingCustomNode("custom1", Some("outputVar"), "addingVariableOptionalEndingCustomNode", "stringVal" -> "'someValue'")

    val validationResult = validator.validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("valid process - custom node with optional end and without output var as ending node") {
    val validProcess = processBase
      .endingCustomNode("custom1", None, "optionalEndingTransformer", "stringVal" -> "'someValue'")

    val validationResult = validator.validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("valid process - custom node with optional end with ongoing node") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "optionalEndingTransformer", "stringVal" -> "'someValue'")
      .sink("out", "''", "dummySink")

    val validationResult = validator.validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("custom1").value shouldBe Map(
      "input" -> Typed[String],
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("out").value shouldBe Map(
      "input" -> Typed[String],
      "outPutVar" -> Unknown,
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("invalid process - non ending custom node ends process - transformation api case") {
    val invalidProcess = processBase
      .endingCustomNode("custom1", Some("outputVar"), "nonEndingCustomNodeReturningTransformation", "stringVal" -> "'someValue'")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(InvalidTailOfBranch("custom1"), _)) =>
    }
  }

  test("invalid process - non ending custom node ends process - non-transformation api case") {
    val invalidProcess = processBase
      .endingCustomNode("custom1", Some("outputVar"), "nonEndingCustomNodeReturningUnit", "stringVal" -> "'someValue'")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(InvalidTailOfBranch("custom1"), _)) =>
    }
  }
  
  test("invalid process with non-existing variable") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#nonExisitngVar")
      .sink("out", "''", "dummySink")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'nonExisitngVar'", "custom1", Some("stringVal"), "#nonExisitngVar"), _)) =>
    }
  }

  test("invalid process with variable of a incorrect type") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "42")
      .sink("out", "''", "dummySink")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Bad expression type, expected: java.lang.String, found: java.lang.Integer", "custom1", Some("stringVal"), "42"), _)) =>
    }
  }

  test("valid process using context transformation api - adding variable") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "addingVariableStreamTransformer")
      .sink("out", "#outPutVar", "dummySink")

    val validationResult = validator.validate(validProcess).result
    validationResult should matchPattern {
      case Valid(_) =>
    }

    val missingOutputVarProcess = processBase
      .customNodeNoOutput("custom1", "addingVariableStreamTransformer")
      .sink("out", "''", "dummySink")

    val missingOutValidationResult = validator.validate(missingOutputVarProcess).result
    missingOutValidationResult.isValid shouldBe false
    val missingOutErrors = missingOutValidationResult.swap.toOption.value.toList
    missingOutErrors should have size 1
    missingOutErrors.head should matchPattern {
      case MissingParameters(params, _) if params == Set("OutputVariable") =>
    }

    val redundantOutputVarProcess = processBase
      .customNode("custom1", "outPutVar", "clearingContextStreamTransformer")
      .sink("out", "#outPutVar", "dummySink")

    val redundantOutValidationResult = validator.validate(redundantOutputVarProcess).result
    redundantOutValidationResult.isValid shouldBe false
    val redundantOutErrors = redundantOutValidationResult.swap.toOption.value.toList
    redundantOutErrors should have size 1
    redundantOutErrors.head should matchPattern {
      case ExpressionParseError(message, _, _, _) if message.startsWith("Unresolved reference 'outPutVar'") =>
    }
  }

  test("valid process using context transformation api - adding variable using compile time evaluated parameter") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "producingTupleTransformer", "numberOfFields" -> "1 + 1")
      .sink("out", "#outPutVar.field1", "dummySink")

    val validationResult = validator.validate(validProcess).result
    validationResult should matchPattern {
      case Valid(_) =>
    }

    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "producingTupleTransformer", "numberOfFields" -> "1 + 1")
      .sink("out", "#outPutVar.field22", "dummySink")

    val validationResult2 = validator.validate(invalidProcess).result
    validationResult2.isValid shouldBe false
    val errors = validationResult2.swap.toOption.value.toList
    errors should have size 1
    errors.head should matchPattern {
      case ExpressionParseError(message, _, _, _) if message.startsWith("There is no property 'field22' in type: object") =>
    }
  }

  test("valid process using context transformation api - union") {
    val validProcess = processWithUnion("#outPutVar.branch1")

    val validationResult = validator.validate(validProcess)
    validationResult.result should matchPattern {
      case Valid(_) =>
    }
    validationResult.variablesInNodes("stringService")("outPutVar") shouldBe TypedObjectTypingResult(
      Map("branch2" -> Typed[Int], "branch1" -> Typed[String]))
  }

  test("invalid process using context transformation api - union") {
    val invalidProcess = processWithUnion("#outPutVar.branch2")
    val validationResult2 = validator.validate(invalidProcess).result

    val errors = validationResult2.swap.toOption.value.toList
    errors should have size 1
    errors.head should matchPattern {
      case ExpressionParseError(
      "Bad expression type, expected: java.lang.String, found: int",
      "stringService", Some("stringParam"), _) =>
    }
  }

  test("global variables in scope after custom context transformation") {
    val process = processWithUnion("#meta.processName")

    val validationResult = validator.validate(process).result
    validationResult should matchPattern {
      case Valid(_) =>
    }
  }

  test("validate nodes after union if validation of part before fails") {
    val process =  EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("sourceId1", "mySource")
          .filter("invalidFilter", "not.a.valid.expression")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .branch("join1", "unionTransformer", Some("outPutVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "#input")
            )
          )
          .processorEnd("stringService", "stringService" , "stringParam" -> "''")
      ))
    val validationResult = validator.validate(process)

    validationResult.variablesInNodes("stringService")("outPutVar") shouldBe TypedObjectTypingResult(Map("branch1" -> Typed[String]))
    val errors = validationResult.result.swap.toList.flatMap(_.toList).map(_.nodeIds)
    errors shouldBe List(Set("invalidFilter"))

  }

  private def processWithUnion(serviceExpression: String) =
    EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder
        .source("sourceId1", "mySource")
        .branchEnd("branch1", "join1"),
      GraphBuilder
        .source("sourceId2", "mySource")
        .branchEnd("branch2", "join1"),
      GraphBuilder
        .branch("join1", "unionTransformer", Some("outPutVar"),
          List(
            "branch1" -> List("key" -> "'key1'", "value" -> "#input"),
            "branch2" -> List("key" -> "'key2'", "value" -> "#input.length()")
          )
        )
        .processorEnd("stringService", "stringService" , "stringParam" -> serviceExpression)
    ))

  test("extract expression typing info from join") {
    val process =
      EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("sourceId1", "mySource")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .branch("join1", "unionTransformer", Some("outPutVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "'ala'"),
              "branch2" -> List("key" -> "'key2'", "value" -> "123")
            )
          )
          .processorEnd("stringService", "stringService" , "stringParam" -> "'123'")
      ))

    val validationResult = validator.validate(process)
    validationResult.result should matchPattern {
      case Valid(_) =>
    }

    validationResult.expressionsInNodes shouldEqual Map(
      ExceptionHandlerNodeId -> Map.empty,
      "sourceId1" -> Map.empty,
      "$edge-branch1-join1" -> Map.empty,
      "sourceId2" -> Map.empty,
      "$edge-branch2-join1" -> Map.empty,
      "join1" -> Map(
        "key-branch1" -> SpelExpressionTypingInfo(Map(PositionRange(0, 6) -> Typed[String])),
        "key-branch2" -> SpelExpressionTypingInfo(Map(PositionRange(0, 6) -> Typed[String])),
        "value-branch1" -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed[String])),
        "value-branch2" -> SpelExpressionTypingInfo(Map(PositionRange(0, 3) -> Typed[Integer]))
      ),
      "stringService" -> Map("stringParam" -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed[String])))
    )
  }

  test("process with enricher") {
    val validProcess = processBase
      .enricher("enricher", "outPutVar", "enricher")
      .sink("out", "''", "dummySink")

    val validationResult = validator.validate(validProcess)
    validationResult.result.isValid shouldBe true
    validationResult.variablesInNodes.get("sourceId").value shouldBe Map(
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("enricher").value shouldBe Map(
      "input" -> Typed[String],
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
    validationResult.variablesInNodes.get("out").value shouldBe Map(
      "input" -> Typed[String],
      "outPutVar" -> Typed[String],
      "meta" -> MetaVariables.typingResult(validProcess.metaData)
    )
  }

  test("join-custom-join should work (branch end is in different part of process)") {
    val validProcess =
      EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("sourceId1", "mySource")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .branch("join1", "unionTransformer", Some("outPutVar"), List("branch1" -> List("key" -> "'key1'", "value" -> "'ala'")))
          .customNode("custom1", "outPutVar3", "producingTupleTransformer", "numberOfFields" -> "2")
          .branchEnd("branch2", "join2"),
        GraphBuilder
          .branch("join2", "unionTransformer", Some("outPutVar2"), List("branch2" -> List("key" -> "'key1'", "value" -> "'ala'")))
          .processorEnd("stringService", "stringService" , "stringParam" -> "'123'")
      ))
    val validationResult = validator.validate(validProcess)

    validationResult.result shouldBe 'valid
  }

  test("eager params in joins") {
    val process =
      EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("sourceId1", "mySource")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .branch("join1", "unionTransformerWithMainBranch", Some("outPutVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "'ala'", "mainBranch" -> "true"),
              "branch2" -> List("key" -> "'key2'", "value" -> "123", "mainBranch" -> "false")
            )
          )
          .sink("sink", "#input" , "dummySink")
      ))

    val validationResult = validator.validate(process)
    validationResult.result should matchPattern {
      case Valid(_) =>
    }
  }

  test("validate union using variables in branches with custom nodes") {
    val process =  EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
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
        .branchEnd("branch2", "join2"),
      GraphBuilder
        .branch("join1", "unionTransformer", Some("unionVariable"),
          List(
            "branch1" -> List("key" -> "'key1'", "value" -> "#variable1"),
            "branch2" -> List("key" -> "'key2'", "value" -> "#variable2")
          )
        )
        .processorEnd("stringService", "stringService" , "stringParam" -> "''")
    ))

    val validationResult = validator.validate(process)

    validationResult.result.isValid shouldBe true
  }

}
