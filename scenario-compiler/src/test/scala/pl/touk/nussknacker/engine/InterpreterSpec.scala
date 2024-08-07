package pl.touk.nussknacker.engine

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.expression.spel.standard.SpelExpression
import pl.touk.nussknacker.engine.InterpreterSpec._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, DesignerWideComponentId, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidFragment
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable => _, _}
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.part.{CustomNodePart, ProcessPart, SinkPart}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.service.{
  EagerServiceWithStaticParametersAndReturnType,
  EnricherContextTransformation
}
import pl.touk.nussknacker.engine.util.{LoggingListener, SynchronousExecutionContextAndIORuntime}

import java.util.{Collections, Optional}
import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class InterpreterSpec extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val resultVariable = "result"

  private val servicesDef = List(
    ComponentDefinition("accountService", AccountService),
    ComponentDefinition("dictService", NameDictService),
    ComponentDefinition("spelNodeService", SpelNodeService),
    ComponentDefinition("withExplicitMethod", WithExplicitDefinitionService),
    ComponentDefinition("spelTemplateService", ServiceUsingSpelTemplate),
    ComponentDefinition("optionTypesService", OptionTypesService),
    ComponentDefinition("optionalTypesService", OptionalTypesService),
    ComponentDefinition("nullableTypesService", NullableTypesService),
    ComponentDefinition("mandatoryTypesService", MandatoryTypesService),
    ComponentDefinition("notBlankTypesService", NotBlankTypesService),
    ComponentDefinition("eagerServiceWithMethod", EagerServiceWithMethod),
    ComponentDefinition("dynamicEagerService", DynamicEagerService),
    ComponentDefinition("eagerServiceWithFixedAdditional", EagerServiceWithFixedAdditional),
    ComponentDefinition("dictParameterEditorService", DictParameterEditorService),
  )

  def listenersDef(listener: Option[ProcessListener] = None): Seq[ProcessListener] =
    listener.toSeq :+ LoggingListener

  private def interpretValidatedProcess(
      process: ValidatedNel[_, CanonicalProcess],
      transaction: Transaction,
      additionalComponents: List[ComponentDefinition],
      listeners: Seq[ProcessListener] = listenersDef()
  ): Any = {
    interpretProcess(process.toOption.get, transaction, additionalComponents, listeners)
  }

  private def interpretProcess(
      scenario: CanonicalProcess,
      transaction: Transaction,
      additionalComponents: List[ComponentDefinition] = servicesDef,
      listeners: Seq[ProcessListener] = listenersDef(),
  ): Any = {
    import Interpreter._

    AccountService.clear()
    NameDictService.clear()

    val metaData = scenario.metaData
    val processCompilerData =
      prepareCompilerData(additionalComponents, listeners)
    val interpreter = processCompilerData.interpreter
    val parts       = failOnErrors(processCompilerData.compile(scenario))

    def compileNode(part: ProcessPart) =
      failOnErrors(processCompilerData.subPartCompiler.compile(part.node, part.validationContext)(metaData).result)

    val initialCtx                    = Context("abc").withVariable(VariableConstants.InputVariableName, transaction)
    val serviceExecutionContext       = ServiceExecutionContext(SynchronousExecutionContextAndIORuntime.syncEc)
    implicit val ioRuntime: IORuntime = SynchronousExecutionContextAndIORuntime.syncIoRuntime

    val resultBeforeSink = interpreter
      .interpret[IO](
        compileNode(parts.sources.head),
        scenario.metaData,
        initialCtx,
        serviceExecutionContext
      )
      .unsafeRunSync()
      .head match {
      case Left(result)         => result
      case Right(exceptionInfo) => throw exceptionInfo.throwable
    }

    resultBeforeSink.reference match {
      case NextPartReference(nextPartId) =>
        val sink = parts.sources.head.nextParts.collectFirst {
          case sink: SinkPart if sink.id == nextPartId                               => sink
          case endingCustomPart: CustomNodePart if endingCustomPart.id == nextPartId => endingCustomPart
        }.get
        interpreter
          .interpret[IO](
            compileNode(sink),
            metaData,
            resultBeforeSink.finalContext,
            serviceExecutionContext
          )
          .unsafeRunSync()
          .head
          .swap
          .toOption
          .get
          .finalContext
          .get(resultVariable)
          .orNull
      // we handle it on other level
      case _: EndReference =>
        null
      case _: FragmentEndReference =>
        null
      case _: DeadEndReference =>
        throw new IllegalStateException("Shouldn't happen")
      case _: JoinReference =>
        throw new IllegalStateException("Shouldn't happen")
    }
  }

  def prepareCompilerData(
      additionalComponents: List[ComponentDefinition],
      listeners: Seq[ProcessListener]
  ): ProcessCompilerData = {
    val components =
      ComponentDefinition("transaction-source", TransactionSource) ::
        ComponentDefinition("dummySink", SinkFactory.noParam(new pl.touk.nussknacker.engine.api.process.Sink {})) ::
        additionalComponents

    val definitions = ModelDefinition(
      ComponentDefinitionWithImplementation
        .forList(components, ComponentsUiConfig.Empty, id => DesignerWideComponentId(id.toString), Map.empty),
      ModelDefinitionBuilder.emptyExpressionConfig,
      ClassExtractionSettings.Default
    )
    val definitionsWithTypes = ModelDefinitionWithClasses(definitions)
    ProcessCompilerData.prepare(
      definitionsWithTypes,
      new SimpleDictRegistry(
        Map("someDictId" -> EmbeddedDictDefinition(Map("someKey" -> "someLabel")))
      ).toEngineRegistry,
      listeners,
      getClass.getClassLoader,
      ProductionServiceInvocationCollector,
      ComponentUseCase.EngineRuntime,
      CustomProcessValidatorLoader.emptyCustomProcessValidator
    )
  }

  private def failOnErrors[T](obj: ValidatedNel[ProcessCompilationError, T]): T = obj match {
    case Valid(c)     => c
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  test("finish with returned value") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .buildSimpleVariable("result-end", resultVariable, "#input.msisdn")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "125")) should equal("125")
  }

  test("filter out based on expression") {

    val falseEnd = GraphBuilder
      .buildSimpleVariable("result-falseEnd", resultVariable, "'d2'")
      .emptySink("end-falseEnd", "dummySink")

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .buildSimpleVariable("result-end", resultVariable, "'d1'")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("d1")
    interpretProcess(process, Transaction(accountId = "122")) should equal("d2")

  }

  test("be able to use SpelExpressionRepr") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "spelNodeService", "expression" -> "#input.accountId == '11' ? 22 : 33")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal("#input.accountId == '11' ? 22 : 33 - Ternary")
  }

  test("ignore disabled filters") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("filter", "false", disabled = Some(true))
      .buildSimpleVariable("result-end", resultVariable, "'d1'")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal("d1")
  }

  test("ignore disabled sinks") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .disabledSink("end", "dummySink")

    assert(interpretProcess(scenario, Transaction()) == null)
  }

  test("ignore disabled processors") {
    object SomeService extends Service {
      var nodes: List[Any] = List[Any]()

      @MethodToInvoke
      def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext): Future[Unit] = Future {
        nodes = id :: nodes
      }
    }

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .disabledProcessor("disabled", "service", params = "id" -> "'disabled'")
      .processor("enabled", "service", "id" -> "'enabled'")
      .disabledProcessorEnd("disabledEnd", "service", "id" -> "'disabled'")
    interpretProcess(scenario, Transaction(), List(ComponentDefinition("service", SomeService)))

    SomeService.nodes shouldBe List("enabled")
  }

  test("invoke processors") {
    val accountId = "333"

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .processor("process", "transactionService", "id" -> "#input.accountId")
      .emptySink("end", "dummySink")

    object TransactionService extends Service {
      var result: Any = ""

      @MethodToInvoke
      def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext): Future[Unit] = Future {
        result = id
      }
    }

    interpretProcess(
      process,
      Transaction(accountId = accountId),
      List(ComponentDefinition("transactionService", TransactionService))
    )

    TransactionService.result should equal(accountId)
  }

  test("build node graph ended-up with processor") {
    val accountId = "333"

    val process =
      ScenarioBuilder
        .streaming("test")
        .source("start", "transaction-source")
        .processorEnd("process", "transactionService", "id" -> "#input.accountId")

    object TransactionService extends Service {
      var result: Any = ""
      @MethodToInvoke
      def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext): Future[Unit] = Future {
        result = id
      }
    }

    interpretProcess(
      process,
      Transaction(accountId = accountId),
      List(ComponentDefinition("transactionService", TransactionService))
    )

    TransactionService.result should equal(accountId)
  }

  test("build node graph ended-up with custom node") {
    val accountId = "333"
    val process =
      ScenarioBuilder
        .streaming("test")
        .source("start", "transaction-source")
        .endingCustomNode("process", None, "transactionCustomComponent")

    object TransactionCustomComponent extends CustomStreamTransformer {
      @MethodToInvoke
      def create(): Unit = {}

      override def canBeEnding: Boolean = true
    }
    val components = List(ComponentDefinition("transactionCustomComponent", TransactionCustomComponent))
    interpretProcess(process, Transaction(accountId = accountId), components)
  }

  test("enrich context") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("filter", "account", "accountService", "id" -> "#input.accountId")
      .buildSimpleVariable("result-end", resultVariable, "#account.name")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("zielonka")
  }

  test("build variable") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("startVB", "transaction-source")
      .buildVariable("buildVar", "fooVar", "accountId" -> "#input.accountId")
      .buildSimpleVariable("result-end", resultVariable, "#fooVar.accountId")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("123")
  }

  test("choose based on expression") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .switch(
        "switch",
        "#input.msisdn",
        "msisdn",
        GraphBuilder.buildSimpleVariable("result-e3end", resultVariable, "'e3'").emptySink("end-e3end", "dummySink"),
        Case(
          "#msisdn == '123'",
          GraphBuilder.buildSimpleVariable("result-e1", resultVariable, "'e1'").emptySink("end-e1", "dummySink")
        ),
        Case(
          "#msisdn == '124'",
          GraphBuilder.buildSimpleVariable("result-e2", resultVariable, "'e2'").emptySink("end-e2", "dummySink")
        )
      )

    interpretProcess(process, Transaction(msisdn = "123")) should equal("e1")
    interpretProcess(process, Transaction(msisdn = "124")) should equal("e2")
    interpretProcess(process, Transaction(msisdn = "125")) should equal("e3")

  }

  test("invoke listeners") {

    var nodeResults = List[String]()

    var serviceResults = Map[String, Any]()

    val listener = new ProcessListener {

      override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
        nodeResults = nodeResults :+ nodeId
      }

      override def endEncountered(
          nodeId: String,
          ref: String,
          context: Context,
          processMetaData: MetaData
      ): Unit = {}

      override def deadEndEncountered(
          lastNodeId: String,
          context: Context,
          processMetaData: MetaData
      ): Unit = {}

      override def serviceInvoked(
          nodeId: String,
          id: String,
          context: Context,
          processMetaData: MetaData,
          result: Try[Any]
      ): Unit = {
        serviceResults = serviceResults + (id -> result)
      }

      override def expressionEvaluated(
          nodeId: String,
          expressionId: String,
          expression: String,
          context: Context,
          processMetaData: MetaData,
          result: Any
      ): Unit = {}

      override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]) = {}
    }

    val process1 = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("enrich", "account", "accountService", "id" -> "#input.accountId")
      .emptySink("end", "dummySink")

    val falseEnd = GraphBuilder
      .emptySink("falseEnd", "dummySink")

    val process2 = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .emptySink("end", "dummySink")

    interpretProcess(process1, Transaction(), listeners = listenersDef(Some(listener)))
    nodeResults should equal(List("start", "enrich", "end"))
    serviceResults should equal(
      Map("accountService" -> Success(Account(marketingAgreement1 = false, "zielonka", "bordo")))
    )

    nodeResults = List()
    interpretProcess(process2, Transaction(), listeners = listenersDef(Some(listener)))
    nodeResults should equal(List("start", "filter", "end"))

    nodeResults = List()
    interpretProcess(process2, Transaction(accountId = "333"), listeners = listenersDef(Some(listener)))
    nodeResults should equal(List("start", "filter", "falseEnd"))

  }

  test("handle fragment") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId")
      .buildSimpleVariable("result-sink", resultVariable, "'result'")
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(
          Filter("f1", "#param == 'a'"),
          List(
            FlatNode(Variable("result", resultVariable, "'deadEnd'")),
            FlatNode(Sink("deadEnd", SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"

    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("handle fragment with unknown as input") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("sub1", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId")
      .fragmentOneOut("sub2", "fragment1", "output", "fragmentResult2", "param" -> "#fragmentResult.result")
      .buildSimpleVariable("result-sink", resultVariable, "'result'")
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[Any])))
        ),
        canonicalnode.FilterNode(
          Filter("f1", "#param == '333'"),
          List(
            FlatNode(Variable("result", resultVariable, "100")),
            FlatNode(Sink("deadEnd", SinkRef("dummySink", List())))
          )
        ),
        canonicalnode.FilterNode(
          Filter("f12", "#param == null"),
          List(
            FlatNode(Variable("result2", resultVariable, "'deadEnd2'")),
            FlatNode(Sink("deadEnd2", SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List(Field("result", "200"))))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd2"
    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe 100
  }

  test("return error when used fragment without input definition") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId")
      .emptySink("end-sink", "dummySink")
    val emptyFragment = CanonicalProcess(MetaData("fragment1", FragmentSpecificData()), List.empty, List.empty)

    val resolved = FragmentResolver(List(emptyFragment)).resolve(process)

    resolved should matchPattern { case Invalid(NonEmptyList(InvalidFragment("fragment1", "sub"), Nil)) =>
    }
  }

  test("handle fragment with two occurrences") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("first", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId")
      .fragmentOneOut("second", "fragment1", "output", "fragmentResult2", "param" -> "#input.msisdn")
      .buildSimpleVariable("result-sink", resultVariable, "'result'")
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(
          Filter("f1", "#param == 'a'"),
          List(
            FlatNode(Variable("result", resultVariable, "'deadEnd'")),
            FlatNode(Sink("deadEnd", SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "deadEnd"
    interpretValidatedProcess(resolved, Transaction(msisdn = "a"), List()) shouldBe "deadEnd"
    interpretValidatedProcess(resolved, Transaction(msisdn = "a", accountId = "a"), List()) shouldBe "result"
  }

  test("handle fragment result in other fragment") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("first", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId")
      .fragmentOneOut("second", "fragment1", "output", "fragmentResult2", "param" -> "#fragmentResult.result")
      .buildSimpleVariable("result-sink", resultVariable, "#fragmentResult2.result")
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List(Field("result", "#param"))))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "333"
  }

  test("handle nested fragment") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("first", "fragment2", "output", "fragmentResult", "param" -> "#input.accountId")
      .buildSimpleVariable("result-sink", resultVariable, "'result'")
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(
          Filter("f1", "#param == 'a'"),
          List(
            FlatNode(Variable("result", "result", "'deadEnd'")),
            FlatNode(Sink("deadEnd", SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val nested = CanonicalProcess(
      MetaData("fragment2", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode.Fragment(
          FragmentInput("sub2", FragmentRef("fragment1", List(NodeParameter(ParameterName("param"), "#param")))),
          Map("output" -> List(FlatNode(FragmentOutputDefinition("sub2Out", "output", List.empty))))
        )
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment, nested)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("handle fragment with more than one output") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragment(
        "sub",
        "fragment1",
        List("param"  -> "#input.accountId"),
        Map("output1" -> "fragmentLeft", "output2" -> "fragmentRight"),
        Map(
          "output1" -> GraphBuilder
            .buildSimpleVariable("result-sink", resultVariable, "#fragmentLeft.left")
            .emptySink("end-sink", "dummySink"),
          "output2" -> GraphBuilder
            .buildSimpleVariable("result-sink2", resultVariable, "#fragmentRight.right")
            .emptySink("end-sink2", "dummySink")
        )
      )

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode.SwitchNode(
          Switch("f1"),
          List(
            canonicalnode.Case(
              "#param == 'a'",
              List(FlatNode(FragmentOutputDefinition("out1", "output1", List(Field("left", "'result1'")))))
            ),
            canonicalnode.Case(
              "#param == 'b'",
              List(FlatNode(FragmentOutputDefinition("out2", "output2", List(Field("right", "'result2'")))))
            )
          ),
          List()
        )
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result1"

    interpretValidatedProcess(resolved, Transaction(accountId = "b"), List()) shouldBe "result2"
  }

  test("handle fragment at end") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentEnd("sub", "fragment1", "param" -> "#input.accountId")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        FlatNode(Variable("result", "result", "'result'")),
        FlatNode(Sink("end", SinkRef("dummySink", List())))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("interprets fragment output fields") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("sub", "fragment1", "output", "output", "toMultiply" -> "2", "multiplyBy" -> "4")
      .buildSimpleVariable("result-sink", resultVariable, "#output.result.toString")
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            "start",
            List(
              FragmentParameter(ParameterName("toMultiply"), FragmentClazzRef[java.lang.Integer]),
              FragmentParameter(ParameterName("multiplyBy"), FragmentClazzRef[java.lang.Integer])
            )
          )
        ),
        FlatNode(FragmentOutputDefinition("subOutput1", "output", List(Field("result", "#toMultiply * #multiplyBy"))))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)
    resolved shouldBe Symbol("valid")
    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List.empty) shouldBe "8"
  }

  test("assure names in fragment don't influence outside var names") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("sub", "fragment1", "outputDefinitionName", "fragmentOut", "param" -> "#input.accountId".spel)
      .buildSimpleVariable("result-sink", resultVariable, "'result'".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        FlatNode(Variable("myVar", "fragmentOut", "'fragmentOut'".spel)),
        FlatNode(FragmentOutputDefinition("out1", "outputDefinitionName", List(Field("paramX", "'paramX'".spel))))
      ),
      List.empty
    )

    val resolved = FragmentResolver(List(fragment)).resolve(process)

    resolved shouldBe Symbol("valid")

    interpretValidatedProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("recognize exception thrown from service as direct exception") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "false")
      .buildSimpleVariable("result-end", resultVariable, "'d1'")
      .emptySink("end-end", "dummySink")

    intercept[CustomException] {
      interpretProcess(process, Transaction(), List(ComponentDefinition("p1", new ThrowingService)))
    }.getMessage shouldBe "Fail?"
  }

  test("recognize exception thrown from service as failed future") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "true")
      .buildSimpleVariable("result-end", resultVariable, "'d1'")
      .emptySink("end-end", "dummySink")

    intercept[CustomException] {
      interpretProcess(process, Transaction(), List(ComponentDefinition("p1", new ThrowingService)))
    }.getMessage shouldBe "Fail?"
  }

  test("not evaluate disabled filters") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("errorFilter", "1/{0, 1}[0] == 0", Option(true))
      .buildSimpleVariable("result-end", resultVariable, "#input.msisdn")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "125")) should equal("125")

  }

  test("invokes services with explicit method") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("ex", "out", "withExplicitMethod", "param1" -> "12333")
      .buildSimpleVariable("result-end", resultVariable, "#out")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal("12333")
  }

  test("invokes services using spel template") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("ex", "out", "spelTemplateService", "template" -> Expression.spelTemplate("Hello #{#input.msisdn}"))
      .buildSimpleVariable("result-end", resultVariable, "#out")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "foo")) should equal("Hello foo")
  }

  test("accept empty expression for option parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "optionTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal(Option.empty)
  }

  test("accept empty expression for optional parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "optionalTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal(Optional.empty())
  }

  test("accept empty expression for nullable parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "nullableTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()).asInstanceOf[String] shouldBe null
  }

  test("not accept no expression for mandatory parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "mandatoryTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression")
      .emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretProcess(process, Transaction())
    }.getMessage shouldBe "Compilation errors: EmptyMandatoryParameter(This field is mandatory and can not be empty,Please fill field for this parameter,ParameterName(expression),customNode)"
  }

  test("not accept blank expression for not blank parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankTypesService", "expression" -> "''")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression")
      .emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretProcess(process, Transaction())
    }.getMessage shouldBe "Compilation errors: BlankParameter(This field value is required and can not be blank,Please fill field value for this parameter,ParameterName(expression),customNode)"
  }

  test("use eager service") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher(
        "customNode",
        "data",
        "eagerServiceWithMethod",
        "eager" -> s"'${EagerServiceWithMethod.checkEager}'",
        "lazy"  -> "{bool: '' == null}"
      )
      .filter("filter", "!#data.bool")
      .buildSimpleVariable("result-end", resultVariable, "#data")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) shouldBe Collections.singletonMap("bool", false)
  }

  test("use dynamic service") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher(
        "customNode",
        "data",
        "dynamicEagerService",
        DynamicEagerService.staticParamName.value -> "'param987'",
        "param987"                                -> "{bool: '' == null}"
      )
      .filter("filter", "!#data.bool")
      .buildSimpleVariable("result-end", resultVariable, "#data")
      .emptySink("end-end", "dummySink")

    val result = interpretProcess(process, Transaction())
    result shouldBe Collections.singletonMap("bool", false)
  }

  test("inject fixed additional variable") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "data", "eagerServiceWithFixedAdditional", "param" -> "#helper.value")
      .buildSimpleVariable("result-end", resultVariable, "#data")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) shouldBe new Helper().value
  }

  test("handle DictParameterEditor with known dictionary and key") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher(
        "customNode",
        "data",
        "dictParameterEditorService",
        "param" -> Expression.dictKeyWithLabel("someKey", Some("someLabel"))
      )
      .buildSimpleVariable("result-end", resultVariable, "#data")
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) shouldBe "someKey"
  }

}

class ThrowingService extends Service {

  @MethodToInvoke
  def invoke(@ParamName("failFuture") failFuture: Boolean): Future[Void] = {
    if (failFuture) Future.failed(new CustomException("Fail?")) else throw new CustomException("Fail?")
  }

}

class CustomException(message: String) extends Exception(message)

object InterpreterSpec {

  case class Transaction(msisdn: String = "123", accountId: String = "123")

  case class Account(marketingAgreement1: Boolean, name: String, name2: String)

  case class LiteralExpressionTypingInfo(typingResult: TypingResult) extends ExpressionTypingInfo

  object AccountService extends Service {

    var invocations = 0

    @MethodToInvoke(returnType = classOf[Account])
    def invoke(@ParamName("id") id: String)(implicit ec: ExecutionContext) = {
      invocations += 1
      Future(Account(marketingAgreement1 = false, "zielonka", "bordo"))
    }

    def clear(): Unit = {
      invocations = 0
    }

  }

  object NameDictService extends Service {

    var invocations = 0

    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("name") name: String) = {
      invocations += 1
      Future.successful("translated" + name)
    }

    def clear(): Unit = {
      invocations = 0
    }

  }

  object SpelNodeService extends Service {

    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("expression") expr: SpelExpressionRepr) = {
      Future.successful(expr.original + " - " + expr.parsed.asInstanceOf[SpelExpression].getAST.getClass.getSimpleName)
    }

  }

  object OptionTypesService extends Service {
    @MethodToInvoke(returnType = classOf[Option[String]])
    def invoke(@ParamName("expression") expr: Option[String]) = Future.successful(expr)
  }

  object OptionalTypesService extends Service {
    @MethodToInvoke(returnType = classOf[Optional[String]])
    def invoke(@ParamName("expression") expr: Optional[String]) = Future.successful(expr)
  }

  object NullableTypesService extends Service {
    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("expression") @Nullable expr: String) = Future.successful(expr)
  }

  object MandatoryTypesService extends Service {
    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("expression") expr: String) = Future.successful(expr)
  }

  object NotBlankTypesService extends Service {
    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("expression") @NotBlank expr: String) = Future.successful(expr)
  }

  object TransactionSource extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke(returnType = classOf[Transaction])
    def create(): api.process.Source = null

  }

  object WithExplicitDefinitionService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(Parameter[Long](ParameterName("param1")))

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(eagerParameters: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        contextId: ContextId,
        metaData: MetaData,
        componentUseCase: ComponentUseCase
    ): Future[AnyRef] = {
      Future.successful(eagerParameters.head._2.toString)
    }

  }

  object ServiceUsingSpelTemplate extends EagerServiceWithStaticParametersAndReturnType {

    private val spelTemplateParameter = Parameter
      .optional[String](ParameterName("template"))
      .copy(isLazyParameter = true, editor = Some(SpelTemplateParameterEditor))

    override def parameters: List[Parameter] = List(spelTemplateParameter)

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        contextId: ContextId,
        metaData: MetaData,
        componentUseCase: ComponentUseCase
    ): Future[AnyRef] = {
      Future.successful(params.head._2.toString)
    }

  }

  object DictParameterEditorService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("param")).copy(
        editor = Some(DictParameterEditor("someDictId"))
      )
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(eagerParameters: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        contextId: ContextId,
        metaData: MetaData,
        componentUseCase: ComponentUseCase
    ): Future[Any] = {
      Future.successful(eagerParameters.head._2.toString)
    }

  }

  class Helper {
    val value = "injected"
  }

  object EagerServiceWithFixedAdditional extends EagerService {

    @MethodToInvoke(returnType = classOf[String])
    def prepare(
        @ParamName("param")
        @AdditionalVariables(Array(new AdditionalVariable(name = "helper", clazz = classOf[Helper])))
        param: String
    ): ServiceInvoker = new ServiceInvoker {

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: InvocationCollectors.ServiceInvocationCollector,
          componentUseCase: ComponentUseCase
      ): Future[Any] = {
        Future.successful(param)
      }

    }

  }

  object EagerServiceWithMethod extends EagerService {

    val checkEager = "@&#%@Q&#"

    @MethodToInvoke
    def prepare(
        @ParamName("eager") eagerOne: String,
        @ParamName("lazy") lazyOne: LazyParameter[AnyRef],
        @OutputVariableName outputVar: String
    )(implicit nodeId: NodeId): ContextTransformation =
      EnricherContextTransformation(
        outputVar,
        lazyOne.returnType, {
          if (eagerOne != checkEager) throw new IllegalArgumentException("Should be not empty?")
          new ServiceInvoker {
            override def invoke(context: Context)(
                implicit ec: ExecutionContext,
                collector: InvocationCollectors.ServiceInvocationCollector,
                componentUseCase: ComponentUseCase
            ): Future[AnyRef] = {
              Future.successful(lazyOne.evaluate(context))
            }
          }
        }
      )

  }

  object DynamicEagerService extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

    override type State = Nothing

    final val staticParamName: ParameterName = ParameterName("static")

    private val staticParamDeclaration = ParameterDeclaration
      .mandatory[String](staticParamName)
      .withCreator()

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): DynamicEagerService.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(List(staticParamDeclaration.createParameter()))
      case TransformationStep((`staticParamName`, DefinedEagerParameter(value: String, _)) :: Nil, _) =>
        val dynamicParamDeclaration = createDynamicParamDeclaration(ParameterName(value))
        NextParameters(dynamicParamDeclaration.createParameter() :: Nil)
      case TransformationStep(
            (`staticParamName`, DefinedEagerParameter(value: String, _)) ::
            (otherName, DefinedLazyParameter(expression)) :: Nil,
            _
          ) if value == otherName.value =>
        FinalResults.forValidation(context)(
          _.withVariable(OutputVariableNameDependency.extract(dependencies), expression, None)
        )
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[Nothing]
    ): ServiceInvoker = {

      val dynamicParamDeclaration = createDynamicParamDeclaration(
        ParameterName(staticParamDeclaration.extractValueUnsafe(params))
      )
      val lazyDynamicParamValue = dynamicParamDeclaration.extractValueUnsafe(params)

      new ServiceInvoker {
        override def invoke(context: Context)(
            implicit ec: ExecutionContext,
            collector: InvocationCollectors.ServiceInvocationCollector,
            componentUseCase: ComponentUseCase
        ): Future[AnyRef] = {
          Future.successful(lazyDynamicParamValue.evaluate(context))
        }
      }
    }

    private def createDynamicParamDeclaration(name: ParameterName) =
      ParameterDeclaration
        .lazyMandatory[AnyRef](name)
        .withCreator()

    override def nodeDependencies: List[NodeDependency] = Nil

  }

}
