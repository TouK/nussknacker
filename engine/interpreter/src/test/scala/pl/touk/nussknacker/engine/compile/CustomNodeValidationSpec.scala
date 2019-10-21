package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import ProcessCompilationError.{ExpressionParseError, MissingParameters, NodeId}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.variables.MetaVariables
import pl.touk.nussknacker.engine.{api, spel}

import scala.collection.Set
import scala.concurrent.Future


class CustomNodeValidationSpec extends FunSuite with Matchers with OptionValues {
  import spel.Implicits._

  class MyProcessConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "myCustomStreamTransformer" -> WithCategories(SimpleStreamTransformer),
      "addingVariableStreamTransformer" -> WithCategories(AddingVariableStreamTransformer),
      "clearingContextStreamTransformer" -> WithCategories(ClearingContextStreamTransformer),
      "producingTupleTransformer" -> WithCategories(ProducingTupleTransformer),
      "unionTransformer" -> WithCategories(UnionTransformer)
    )
    override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
      "mySource" -> WithCategories(SimpleStringSource))
    override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
      "dummySink" -> WithCategories(SinkFactory.noParam(new Sink { override def testDataOutput = None })))

    override def services(config: Config): Map[String, WithCategories[Service]] = Map(
      "stringService" -> WithCategories(SimpleStringService)
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
               (implicit nodeId: NodeId) = {
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
                @OutputVariableName variableName: String) = {
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

  val processBase = EspProcessBuilder.id("proc1").exceptionHandler().source("sourceId", "mySource")
  val objectWithMethodDef = ProcessDefinitionExtractor.extractObjectWithMethods(new MyProcessConfigCreator, ConfigFactory.empty)
  val validator = ProcessValidator.default(objectWithMethodDef)

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

  test("invalid process with non-existing variable") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#nonExisitngVar")
      .sink("out", "''", "dummySink")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference nonExisitngVar", "custom1",Some("stringVal"), "#nonExisitngVar"), _))  =>
    }
  }

  test("invalid process with variable of a incorrect type") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "42")
      .sink("out", "''", "dummySink")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Bad expression type, expected: java.lang.String, found: java.lang.Integer", "custom1",Some("stringVal"), "42"), _))  =>
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
      case ExpressionParseError(message, _, _, _) if message.startsWith("Unresolved reference outPutVar") =>
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
    def process(serviceExpression: String) =
      EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("sourceId1", "mySource")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .branch("join1", "unionTransformer", Some("outPutVar"),
            // TODO JOIN: use branch context in expressions
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "'ala'"),
              "branch2" -> List("key" -> "'key2'", "value" -> "123")
            )
          )
          .processorEnd("stringService", "stringService" , "stringParam" -> serviceExpression)
      ))
    val validProcess = process("#outPutVar.branch1")

    val validationResult = validator.validate(validProcess).result
    validationResult should matchPattern {
      case Valid(_) =>
    }

    val invalidProcess = process("#outPutVar.branch2")
    val validationResult2 = validator.validate(invalidProcess).result
    val errors = validationResult2.swap.toOption.value.toList
    errors should have size 1
    errors.head should matchPattern {
      case ExpressionParseError(
      "Bad expression type, expected: java.lang.String, found: java.lang.Integer",
      "stringService", Some("stringParam"), _) =>
    }
  }

}