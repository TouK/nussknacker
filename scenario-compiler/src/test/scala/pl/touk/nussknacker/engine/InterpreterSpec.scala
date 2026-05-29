package pl.touk.nussknacker.engine

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.expression.spel.standard.SpelExpression
import pl.touk.nussknacker.engine.InterpreterSpec._
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.RuntimeMode.{Live, Test}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.ProcessListener.Transition
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  DesignerWideComponentId,
  NodesDeploymentData,
  UnboundedStreamComponent
}
import pl.touk.nussknacker.engine.api.context.{
  ContextTransformation,
  PartSubGraphCompilationError,
  ProcessCompilationError,
  ValidationContext
}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidFragment
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable => _, _}
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}
import pl.touk.nussknacker.engine.api.exception.{NuExceptionInfo, ParameterRuntimeValidationException}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.validation.CustomValidator
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.SingleInputNodeInputValidationContext
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.compiledgraph.part.{CustomNodePart, ProcessPart, SinkPart}
import pl.touk.nussknacker.engine.definition.component.Components
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.testcomponents.SpelTemplatePartsService
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.{LoggingListener, SynchronousExecutionContextAndIORuntime}
import pl.touk.nussknacker.engine.util.service.{
  EagerServiceWithStaticParametersAndReturnType,
  EnricherContextTransformation
}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import java.util.{Collections, Objects, Optional}
import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class InterpreterSpec extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  val resultVariable = "result"

  private val servicesDef = List(
    ComponentDefinition("accountService", AccountService),
    ComponentDefinition("dictService", NameDictService),
    ComponentDefinition("spelNodeService", SpelNodeService),
    ComponentDefinition("withExplicitMethod", WithExplicitDefinitionService),
    ComponentDefinition("spelTemplateService", ServiceUsingSpelTemplate),
    ComponentDefinition("spelTemplatePartsService", SpelTemplatePartsService),
    ComponentDefinition("optionTypesService", OptionTypesService),
    ComponentDefinition("optionTypesDifferentLanguagesService", optionTypesDifferentLanguagesService),
    ComponentDefinition("optionalTypesService", OptionalTypesService),
    ComponentDefinition("nullableTypesService", NullableTypesService),
    ComponentDefinition("mandatoryTypesService", MandatoryTypesService),
    ComponentDefinition("notBlankTypesService", NotBlankTypesService),
    ComponentDefinition("notNullTypesService", NotNullTypesService),
    ComponentDefinition("nullSensitiveService", NullSensitiveService),
    ComponentDefinition("customValidatorTypesService", CustomValidatorTypesService),
    ComponentDefinition("notBlankRuntimeTypesService", NotBlankRuntimeTypesService),
    ComponentDefinition("dualNotBlankTypesService", DualNotBlankTypesService),
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
      runtimeMode: RuntimeMode = RuntimeMode.Live,
  ): Any = {
    import Interpreter._

    AccountService.clear()
    NameDictService.clear()

    val jobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    val processCompilerData =
      prepareCompilerData(jobData, additionalComponents, listeners, runtimeMode)
    val interpreter = processCompilerData.interpreter
    implicit val engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies =
      EngineScenarioCompilationDependencies.empty
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)
    val parts = failOnErrors(processCompilerData.compile(scenario))

    def compileNode(part: ProcessPart) =
      failOnErrors(
        processCompilerData.subPartCompiler
          .compile(part.node, SingleInputNodeInputValidationContext(part.validationContext))
          .result
      )

    val initialCtx                    = Context.dummy.withVariable(VariableConstants.InputVariableName, transaction)
    val serviceExecutionContext       = ServiceExecutionContext(SynchronousExecutionContextAndIORuntime.syncEc)
    implicit val ioRuntime: IORuntime = SynchronousExecutionContextAndIORuntime.syncIoRuntime

    val resultBeforeSink = interpreter
      .interpret[IO](
        compileNode(parts.sources.head),
        jobData,
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
          case sink: SinkPart if sink.id.value == nextPartId                               => sink
          case endingCustomPart: CustomNodePart if endingCustomPart.id.value == nextPartId => endingCustomPart
        }.get
        interpreter
          .interpret[IO](
            compileNode(sink),
            jobData,
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
      jobData: JobData,
      additionalComponents: List[ComponentDefinition],
      listeners: Seq[ProcessListener],
      runtimeMode: RuntimeMode,
  ): ProcessCompilerData = {
    val globalParametersConfig = GlobalParametersConfig.default
    val components =
      ComponentDefinition("transaction-source", TransactionSource) ::
        ComponentDefinition("dummySink", SinkFactory.noParam(new pl.touk.nussknacker.engine.api.process.Sink {})) ::
        additionalComponents

    val definitions = ModelDefinition(
      Components
        .forList(
          components,
          ComponentsUiConfig.Empty,
          id => DesignerWideComponentId(id.toString),
          Map.empty,
          globalParametersConfig,
          ComponentDefinitionExtractionMode.FinalDefinition
        ),
      ModelDefinitionBuilder.emptyExpressionConfig,
      ClassExtractionSettings.Default,
      allowEndingScenarioWithoutSink = false,
      globalParametersConfig = globalParametersConfig,
    )
    val definitionsWithTypes = ModelDefinitionWithClasses(definitions)
    ProcessCompilerData.prepare(
      jobData,
      definitionsWithTypes,
      new SimpleDictRegistry(
        Map("someDictId" -> EmbeddedDictDefinition(Map("someKey" -> "someLabel")))
      ).toEngineRegistry,
      listeners,
      getClass.getClassLoader,
      ProductionServiceInvocationCollector,
      runtimeMode,
      CustomProcessValidatorLoader.emptyCustomProcessValidator,
      NodesDeploymentData.empty,
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
      .buildSimpleVariable("result-end", resultVariable, "#input.msisdn".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "125")) should equal("125")
  }

  test("filter out based on expression") {

    val falseEnd = GraphBuilder
      .buildSimpleVariable("result-falseEnd", resultVariable, "'d2'".spel)
      .emptySink("end-falseEnd", "dummySink")

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'".spel, falseEnd)
      .buildSimpleVariable("result-end", resultVariable, "'d1'".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("d1")
    interpretProcess(process, Transaction(accountId = "122")) should equal("d2")

  }

  test("be able to use SpelExpressionRepr") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher(
        "customNode",
        "rawExpression",
        "spelNodeService",
        "expression" -> "#input.accountId == '11' ? 22 : 33".spel
      )
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal("#input.accountId == '11' ? 22 : 33 - Ternary")
  }

  test("ignore disabled filters") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("filter", "false".spel, disabled = Some(true))
      .buildSimpleVariable("result-end", resultVariable, "'d1'".spel)
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
      .disabledProcessor("disabled", "service", params = "id" -> "'disabled'".spel)
      .processor("enabled", "service", "id" -> "'enabled'".spel)
      .disabledProcessorEnd("disabledEnd", "service", "id" -> "'disabled'".spel)
    interpretProcess(scenario, Transaction(), List(ComponentDefinition("service", SomeService)))

    SomeService.nodes shouldBe List("enabled")
  }

  test("invoke processors") {
    val accountId = "333"

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .processor("process", "transactionService", "id" -> "#input.accountId".spel)
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
        .processorEnd("process", "transactionService", "id" -> "#input.accountId".spel)

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
      def create(): DummyTransformer.type = DummyTransformer

      override def canBeEnding: Boolean = true

      object DummyTransformer
    }
    val components = List(ComponentDefinition("transactionCustomComponent", TransactionCustomComponent))
    interpretProcess(process, Transaction(accountId = accountId), components)
  }

  test("enrich context") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("filter", "account", "accountService", "id" -> "#input.accountId".spel)
      .buildSimpleVariable("result-end", resultVariable, "#account.name".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("zielonka")
  }

  test("build variable") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("startVB", "transaction-source")
      .buildVariable("buildVar", "fooVar", "accountId" -> "#input.accountId".spel)
      .buildSimpleVariable("result-end", resultVariable, "#fooVar.accountId".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("123")
  }

  test("unset variable and set it again") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .buildSimpleVariable("setVar", "fooVar", "'123'".spel)
      .unsetVariables("unsetVar", "fooVar")
      .buildSimpleVariable("setVarAgain", "fooVar", "'456'".spel)
      .buildSimpleVariable("result-end", resultVariable, "#fooVar".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(accountId = "123")) should equal("456")
  }

  test("choose based on expression") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .switch(
        "switch",
        "#input.msisdn".spel,
        "msisdn",
        GraphBuilder
          .buildSimpleVariable("result-e3end", resultVariable, "'e3'".spel)
          .emptySink("end-e3end", "dummySink"),
        Case(
          "#msisdn == '123'".spel,
          GraphBuilder.buildSimpleVariable("result-e1", resultVariable, "'e1'".spel).emptySink("end-e1", "dummySink")
        ),
        Case(
          "#msisdn == '124'".spel,
          GraphBuilder.buildSimpleVariable("result-e2", resultVariable, "'e2'".spel).emptySink("end-e2", "dummySink")
        )
      )

    interpretProcess(process, Transaction(msisdn = "123")) should equal("e1")
    interpretProcess(process, Transaction(msisdn = "124")) should equal("e2")
    interpretProcess(process, Transaction(msisdn = "125")) should equal("e3")

  }

  test("invoke listeners") {

    var nodeResults = List[NodeId]()

    var serviceResults = Map[String, Any]()

    val listener = new ProcessListener {

      override def nodeEntered(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit = {
        nodeResults = nodeResults :+ nodeId
      }

      override def transitionToNextNode(
          transition: Transition,
          context: Context,
          processMetaData: MetaData
      ): Unit = ()

      override def processingFinishedInNode(
          nodeId: NodeId,
          context: Context,
          processMetaData: MetaData
      ): Unit = ()

      override def endEncountered(
          nodeId: NodeId,
          ref: String,
          context: Context,
          processMetaData: MetaData
      ): Unit = {}

      override def deadEndEncountered(
          lastNodeId: NodeId,
          context: Context,
          processMetaData: MetaData
      ): Unit = {}

      override def serviceInvoked(
          nodeId: NodeId,
          id: String,
          context: Context,
          processMetaData: MetaData,
          result: Try[Any]
      ): Unit = {
        serviceResults = serviceResults + (id -> result)
      }

      override def expressionEvaluated(
          nodeId: NodeId,
          expressionId: String,
          expression: String,
          context: Context,
          processMetaData: MetaData,
          result: Any
      ): Unit = {}

      override def exceptionThrown(exceptionInfo: NuExceptionInfo) = {}
    }

    val process1 = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("enrich", "account", "accountService", "id" -> "#input.accountId".spel)
      .emptySink("end", "dummySink")

    val falseEnd = GraphBuilder
      .emptySink("falseEnd", "dummySink")

    val process2 = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'".spel, falseEnd)
      .emptySink("end", "dummySink")

    interpretProcess(process1, Transaction(), listeners = listenersDef(Some(listener)))
    nodeResults should equal(List(NodeId("start"), NodeId("enrich"), NodeId("end")))
    serviceResults should equal(
      Map("accountService" -> Success(Account(marketingAgreement1 = false, "zielonka", "bordo")))
    )

    nodeResults = List()
    interpretProcess(process2, Transaction(), listeners = listenersDef(Some(listener)))
    nodeResults should equal(List(NodeId("start"), NodeId("filter"), NodeId("end")))

    nodeResults = List()
    interpretProcess(process2, Transaction(accountId = "333"), listeners = listenersDef(Some(listener)))
    nodeResults should equal(List(NodeId("start"), NodeId("filter"), NodeId("falseEnd")))

  }

  test("handle fragment") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId".spel)
      .buildSimpleVariable("result-sink", resultVariable, "'result'".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(
          Filter(NodeId("f1"), NodeName("f1"), "#param == 'a'".spel),
          List(
            FlatNode(Variable(NodeId("result"), NodeName("result"), resultVariable, "'deadEnd'".spel)),
            FlatNode(Sink(NodeId("deadEnd"), NodeName("deadEnd"), SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
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
      .fragmentOneOut("sub1", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId".spel)
      .fragmentOneOut("sub2", "fragment1", "output", "fragmentResult2", "param" -> "#fragmentResult.result".spel)
      .buildSimpleVariable("result-sink", resultVariable, "'result'".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[Any]))
          )
        ),
        canonicalnode.FilterNode(
          Filter(NodeId("f1"), NodeName("f1"), "#param == '333'".spel),
          List(
            FlatNode(Variable(NodeId("result"), NodeName("result"), resultVariable, "100".spel)),
            FlatNode(Sink(NodeId("deadEnd"), NodeName("deadEnd"), SinkRef("dummySink", List())))
          )
        ),
        canonicalnode.FilterNode(
          Filter(NodeId("f12"), NodeName("f12"), "#param == null".spel),
          List(
            FlatNode(Variable(NodeId("result2"), NodeName("result2"), resultVariable, "'deadEnd2'".spel)),
            FlatNode(Sink(NodeId("deadEnd2"), NodeName("deadEnd2"), SinkRef("dummySink", List())))
          )
        ),
        FlatNode(
          FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List(Field("result", "200".spel)))
        )
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
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId".spel)
      .emptySink("end-sink", "dummySink")
    val emptyFragment = CanonicalProcess(MetaData("fragment1", FragmentSpecificData()), List.empty, List.empty)

    val resolved = FragmentResolver(List(emptyFragment)).resolve(process)

    resolved should matchPattern { case Invalid(NonEmptyList(InvalidFragment("fragment1", NodeId("sub"), _), Nil)) =>
    }
  }

  test("handle fragment with two occurrences") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "transaction-source")
      .fragmentOneOut("first", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId".spel)
      .fragmentOneOut("second", "fragment1", "output", "fragmentResult2", "param" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-sink", resultVariable, "'result'".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(
          Filter(NodeId("f1"), NodeName("f1"), "#param == 'a'".spel),
          List(
            FlatNode(Variable(NodeId("result"), NodeName("result"), resultVariable, "'deadEnd'".spel)),
            FlatNode(Sink(NodeId("deadEnd"), NodeName("deadEnd"), SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
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
      .fragmentOneOut("first", "fragment1", "output", "fragmentResult", "param" -> "#input.accountId".spel)
      .fragmentOneOut("second", "fragment1", "output", "fragmentResult2", "param" -> "#fragmentResult.result".spel)
      .buildSimpleVariable("result-sink", resultVariable, "#fragmentResult2.result".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        FlatNode(
          FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List(Field("result", "#param".spel)))
        )
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
      .fragmentOneOut("first", "fragment2", "output", "fragmentResult", "param" -> "#input.accountId".spel)
      .buildSimpleVariable("result-sink", resultVariable, "'result'".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(
          Filter(NodeId("f1"), NodeName("f1"), "#param == 'a'".spel),
          List(
            FlatNode(Variable(NodeId("result"), NodeName("result"), "result", "'deadEnd'".spel)),
            FlatNode(Sink(NodeId("deadEnd"), NodeName("deadEnd"), SinkRef("dummySink", List())))
          )
        ),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

    val nested = CanonicalProcess(
      MetaData("fragment2", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.Fragment(
          FragmentInput(
            NodeId("sub2"),
            NodeName("sub2"),
            FragmentRef("fragment1", List(NodeParameter(ParameterName("param"), "#param".spel)))
          ),
          Map(
            "output" -> List(
              FlatNode(FragmentOutputDefinition(NodeId("sub2Out"), NodeName("sub2Out"), "output", List.empty))
            )
          )
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
        List("param"  -> "#input.accountId".spel),
        Map("output1" -> "fragmentLeft", "output2" -> "fragmentRight"),
        Map(
          "output1" -> GraphBuilder
            .buildSimpleVariable("result-sink", resultVariable, "#fragmentLeft.left".spel)
            .emptySink("end-sink", "dummySink"),
          "output2" -> GraphBuilder
            .buildSimpleVariable("result-sink2", resultVariable, "#fragmentRight.right".spel)
            .emptySink("end-sink2", "dummySink")
        )
      )

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.SwitchNode(
          Switch(NodeId("f1"), NodeName("f1"), None, None),
          List(
            canonicalnode.Case(
              "#param == 'a'".spel,
              List(
                FlatNode(
                  FragmentOutputDefinition(
                    NodeId("out1"),
                    NodeName("out1"),
                    "output1",
                    List(Field("left", "'result1'".spel))
                  )
                )
              )
            ),
            canonicalnode.Case(
              "#param == 'b'".spel,
              List(
                FlatNode(
                  FragmentOutputDefinition(
                    NodeId("out2"),
                    NodeName("out2"),
                    "output2",
                    List(Field("right", "'result2'".spel))
                  )
                )
              )
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
      .fragmentEnd("sub", "fragment1", "param" -> "#input.accountId".spel)

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        FlatNode(Variable(NodeId("result"), NodeName("result"), "result", "'result'".spel)),
        FlatNode(Sink(NodeId("end"), NodeName("end"), SinkRef("dummySink", List())))
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
      .fragmentOneOut("sub", "fragment1", "output", "output", "toMultiply" -> "2".spel, "multiplyBy" -> "4".spel)
      .buildSimpleVariable("result-sink", resultVariable, "#output.result.toString".spel)
      .emptySink("end-sink", "dummySink")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(
              FragmentParameter(ParameterName("toMultiply"), FragmentClazzRef[java.lang.Integer]),
              FragmentParameter(ParameterName("multiplyBy"), FragmentClazzRef[java.lang.Integer])
            )
          )
        ),
        FlatNode(
          FragmentOutputDefinition(
            NodeId("subOutput1"),
            NodeName("subOutput1"),
            "output",
            List(Field("result", "#toMultiply * #multiplyBy".spel))
          )
        )
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
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        FlatNode(Variable(NodeId("myVar"), NodeName("myVar"), "fragmentOut", "'fragmentOut'".spel)),
        FlatNode(
          FragmentOutputDefinition(
            NodeId("out1"),
            NodeName("out1"),
            "outputDefinitionName",
            List(Field("paramX", "'paramX'".spel))
          )
        )
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
      .processor("processor", "p1", "failFuture" -> "false".spel)
      .buildSimpleVariable("result-end", resultVariable, "'d1'".spel)
      .emptySink("end-end", "dummySink")

    intercept[CustomException] {
      interpretProcess(process, Transaction(), List(ComponentDefinition("p1", new ThrowingService)))
    }.getMessage shouldBe "Fail?"
  }

  test("recognize exception thrown from service as failed future") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "true".spel)
      .buildSimpleVariable("result-end", resultVariable, "'d1'".spel)
      .emptySink("end-end", "dummySink")

    intercept[CustomException] {
      interpretProcess(process, Transaction(), List(ComponentDefinition("p1", new ThrowingService)))
    }.getMessage shouldBe "Fail?"
  }

  test("not evaluate disabled filters") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .filter("errorFilter", "1/{0, 1}[0] == 0".spel, Option(true))
      .buildSimpleVariable("result-end", resultVariable, "#input.msisdn".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "125")) should equal("125")

  }

  test("invokes services with explicit method") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("ex", "out", "withExplicitMethod", "param1" -> "12333".spel)
      .buildSimpleVariable("result-end", resultVariable, "#out".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal("12333")
  }

  test("invokes services using spel template") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("ex", "out", "spelTemplateService", "template" -> Expression.spelTemplate("Hello #{#input.msisdn}"))
      .buildSimpleVariable("result-end", resultVariable, "#out".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "foo")) should equal("Hello foo")
  }

  test("accept empty expression for option parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "optionTypesService", "expression" -> "".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal(Option.empty)
  }

  test("accept empty expression for option parameter for different languages") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher(
        "customNode",
        "rawExpression",
        "optionTypesDifferentLanguagesService",
        "json"         -> "".jsonExpression,
        "jsonTemplate" -> "".jsonTemplate,
        "dict"         -> Expression(Language.DictKeyWithLabel, ""),
        "tabularData"  -> Expression(Language.TabularDataDefinition, ""),
      )
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal(Option.empty)
  }

  test("accept empty expression for optional parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "optionalTypesService", "expression" -> "".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) should equal(Optional.empty())
  }

  test("accept empty expression for nullable parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "nullableTypesService", "expression" -> "".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()).asInstanceOf[String] shouldBe null
  }

  test("not accept no expression for mandatory parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "mandatoryTypesService", "expression" -> "".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretProcess(process, Transaction())
    }.getMessage shouldBe "Compilation errors: EmptyMandatoryParameter(Field: expression is mandatory and can not be empty,Please fill field for this parameter,expression,customNode)"
  }

  test("not accept blank expression for not blank parameter") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankTypesService", "expression" -> "''".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretProcess(process, Transaction())
    }.getMessage shouldBe "Compilation errors: BlankParameter(This field value is required and can not be blank,Please fill field value for this parameter,expression,customNode)"
  }

  test("CompileTimeValidator does not run at runtime for dynamic expression evaluating to blank") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "")) shouldBe ""
  }

  test("CompileTimeValidator does not run at runtime for dynamic expression evaluating to a valid value") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "valid-value")) shouldBe "valid-value"
  }

  test("CompileTimeValidator does not run at runtime for dynamic expression evaluating to null") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notNullTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = null)).asInstanceOf[String] shouldBe null
  }

  test("null reaches service and causes NullPointerException when component is null-sensitive") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "nullSensitiveService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[NullPointerException] {
      interpretProcess(process, Transaction(msisdn = null))
    }
  }

  test("CompileTimeValidator annotated with @CustomValidator does not run at runtime for dynamic expression") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "customValidatorTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "")) shouldBe ""
  }

  test("not throw when @CustomValidator passes for dynamic expression") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "customValidatorTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "valid-value")) shouldBe "valid-value"
  }

  test("RuntimeValidator fires and throws ParameterRuntimeValidationException during scenario execution") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankRuntimeTypesService", "expression" -> "''".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[ParameterRuntimeValidationException] {
      interpretProcess(process, Transaction())
    }.getMessage should include("expression")
  }

  test("RuntimeValidator allows valid value through during scenario execution") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankRuntimeTypesService", "expression" -> "'valid'".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) shouldBe "valid"
  }

  test("RuntimeValidator runs at runtime for dynamic expression evaluating to blank") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankRuntimeTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[ParameterRuntimeValidationException] {
      interpretProcess(process, Transaction(msisdn = ""))
    }.getMessage should include("expression")
  }

  test("dual validator (compile-time+runtime): compile-time fires for blank literal") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "dualNotBlankTypesService", "expression" -> "''".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretProcess(process, Transaction())
    }.getMessage shouldBe "Compilation errors: CustomParameterValidationError(Value must not be blank,Please provide a non-blank value,expression,customNode)"
  }

  test("dual validator (compile-time+runtime): runtime fires for dynamic expression evaluating to blank") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "dualNotBlankTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    intercept[ParameterRuntimeValidationException] {
      interpretProcess(process, Transaction(msisdn = ""))
    }.getMessage should include("expression")
  }

  test("dual validator (compile-time+runtime): valid value passes both") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "dualNotBlankTypesService", "expression" -> "#input.msisdn".spel)
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(msisdn = "valid-value")) shouldBe "valid-value"
  }

  // Unit-level tests for ExpressionEvaluator runtime validation — exercise evaluateParameter directly, not via interpretProcess

  test("ExpressionEvaluator: RuntimeValidator fires and throws when value is invalid") {
    implicit val nodeId: NodeId   = NodeId("testNode")
    val metaData                  = MetaData("TestProcess", StreamMetaData())
    implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val evaluator = ExpressionEvaluator.optimizedEvaluator(
      GlobalVariablesPreparer(ModelDefinitionBuilder.empty.build.expressionConfig),
      Nil,
    )
    val param = CompiledParameter(
      name = ParameterName("param"),
      expression = new FixedValueExpression(""),
      shouldBeWrappedWithScalaOption = false,
      shouldBeWrappedWithJavaOptional = false,
      typingInfo = null,
      validators = List(NotBlankRuntimeValidator),
    )

    intercept[ParameterRuntimeValidationException] {
      evaluator.evaluateParameter(param, Context.dummy)
    }.getMessage should include("param")
  }

  test("ExpressionEvaluator: RuntimeValidator does not throw when value is valid") {
    implicit val nodeId: NodeId   = NodeId("testNode")
    val metaData                  = MetaData("TestProcess", StreamMetaData())
    implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val evaluator = ExpressionEvaluator.optimizedEvaluator(
      GlobalVariablesPreparer(ModelDefinitionBuilder.empty.build.expressionConfig),
      Nil,
    )
    val param = CompiledParameter(
      name = ParameterName("param"),
      expression = new FixedValueExpression("valid"),
      shouldBeWrappedWithScalaOption = false,
      shouldBeWrappedWithJavaOptional = false,
      typingInfo = null,
      validators = List(NotBlankRuntimeValidator),
    )

    evaluator.evaluateParameter(param, Context.dummy).map(_.value) shouldBe Right("valid")
  }

  test("ExpressionEvaluator: no exception when no RuntimeValidator is attached") {
    implicit val nodeId: NodeId   = NodeId("testNode")
    val metaData                  = MetaData("TestProcess", StreamMetaData())
    implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val evaluator = ExpressionEvaluator.optimizedEvaluator(
      GlobalVariablesPreparer(ModelDefinitionBuilder.empty.build.expressionConfig),
      Nil,
    )
    val param = CompiledParameter(
      name = ParameterName("param"),
      expression = new FixedValueExpression(""),
      shouldBeWrappedWithScalaOption = false,
      shouldBeWrappedWithJavaOptional = false,
      typingInfo = null,
      validators = Nil,
    )

    evaluator.evaluateParameter(param, Context.dummy).map(_.value) shouldBe Right("")
  }

  test("ExpressionEvaluator: exception thrown inside RuntimeValidator.isValid propagates as-is, not wrapped") {
    implicit val nodeId: NodeId   = NodeId("testNode")
    val metaData                  = MetaData("TestProcess", StreamMetaData())
    implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val evaluator = ExpressionEvaluator.optimizedEvaluator(
      GlobalVariablesPreparer(ModelDefinitionBuilder.empty.build.expressionConfig),
      Nil,
    )
    val customValidator = new RuntimeValidator {
      override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
          implicit nodeId: NodeId
      ): Validated[ParameterRuntimeValidationError, Unit] =
        throw new IllegalStateException("custom error")
    }
    val param = CompiledParameter(
      name = ParameterName("param"),
      expression = new FixedValueExpression("anything"),
      shouldBeWrappedWithScalaOption = false,
      shouldBeWrappedWithJavaOptional = false,
      typingInfo = null,
      validators = List(customValidator),
    )

    intercept[IllegalStateException] {
      evaluator.evaluateParameter(param, Context.dummy)
    }.getMessage shouldBe "custom error"
  }

  test("use eager service") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher(
        "customNode",
        "data",
        "eagerServiceWithMethod",
        "eager" -> s"'${EagerServiceWithMethod.checkEager}'".spel,
        "lazy"  -> "{bool: '' == null}".spel
      )
      .filter("filter", "!#data.bool".spel)
      .buildSimpleVariable("result-end", resultVariable, "#data".spel)
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
        DynamicEagerService.staticParamName.value -> "'param987'".spel,
        "param987"                                -> "{bool: '' == null}".spel
      )
      .filter("filter", "!#data.bool".spel)
      .buildSimpleVariable("result-end", resultVariable, "#data".spel)
      .emptySink("end-end", "dummySink")

    val result = interpretProcess(process, Transaction())
    result shouldBe Collections.singletonMap("bool", false)
  }

  test("should invoke enricher in case of live mode even if mocked output is configured") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricherWithMockExpression(
        "ex",
        "out",
        "withExplicitMethod",
        mockExpression = "'2222'".spel,
        params = "param1" -> "1111".spel
      )
      .buildSimpleVariable("result-end", resultVariable, "#out".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(), runtimeMode = Live) should equal("1111")
  }

  test(
    "should use configured mocked output as output instead of invoking enricher in case of test mode - spel expression case"
  ) {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricherWithMockExpression(
        "ex",
        "out",
        "withExplicitMethod",
        mockExpression = "'2222'".spel,
        params = "param1" -> "1111".spel
      )
      .buildSimpleVariable("result-end", resultVariable, "#out".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(), runtimeMode = Test) should equal("2222")
  }

  test(
    "should use configured mocked output as output instead of invoking enricher in case of test mode - json template expression case"
  ) {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricherWithMockExpression(
        "ex",
        "out",
        "withExplicitMethod",
        mockExpression = Expression(Language.JsonTemplate, "\"2222\""),
        params = "param1" -> "1111".spel
      )
      .buildSimpleVariable("result-end", resultVariable, "#out".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(), runtimeMode = Test) should equal("2222")
  }

  test(
    "should use configured mocked output as output instead of invoking enricher in case of test mode - json template null expression case"
  ) {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricherWithMockExpression(
        "ex",
        "out",
        "withExplicitMethod",
        mockExpression = Expression(Language.JsonTemplate, "null"),
        params = "param1" -> "1111".spel
      )
      .buildSimpleVariable("result-end", resultVariable, "#out".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction(), runtimeMode = Test) shouldBe null.asInstanceOf[String]
  }

  test("inject fixed additional variable") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "transaction-source")
      .enricher("customNode", "data", "eagerServiceWithFixedAdditional", "param" -> "#helper.value".spel)
      .buildSimpleVariable("result-end", resultVariable, "#data".spel)
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
      .buildSimpleVariable("result-end", resultVariable, "#data".spel)
      .emptySink("end-end", "dummySink")

    interpretProcess(process, Transaction()) shouldBe "someKey"
  }

  test("service using spel template rendered parts") {
    val testCases = Seq(
      (
        "subexpression and literal value",
        s"Hello#{#input.msisdn}",
        Transaction(msisdn = "foo"),
        "[Hello]-literal[foo]-subexpression"
      ),
      (
        "single literal value",
        "Hello",
        Transaction(msisdn = "foo"),
        "[Hello]-literal"
      ),
      (
        "single function call expression",
        "#{#input.msisdn.toString()}",
        Transaction(msisdn = "foo"),
        "[foo]-subexpression"
      ),
      (
        "empty value",
        "",
        Transaction(msisdn = "foo"),
        "[]-literal"
      ),
    )
    for ((description, templateExpression, inputTransaction, expectedOutput) <- testCases) {
      withClue(s"Test case: $description") {
        val process = ScenarioBuilder
          .streaming("test")
          .source("start", "transaction-source")
          .enricher(
            "ex",
            "out",
            "spelTemplatePartsService",
            "template" -> Expression.spelTemplate(templateExpression)
          )
          .buildSimpleVariable("result-end", resultVariable, "#out".spel)
          .emptySink("end-end", "dummySink")

        interpretProcess(process, inputTransaction) should equal(expectedOutput)
      }
    }
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

  object optionTypesDifferentLanguagesService extends Service {

    @MethodToInvoke(returnType = classOf[Option[Any]])
    def invoke(
        @ParamName("json") @Editor(`type` = EditorType.JSON_EDITOR) jsonValue: Option[Json],
        @ParamName("jsonTemplate") @Editor(`type` = EditorType.JSON_TEMPLATE_EDITOR) jsonTemplateValue: Option[Any],
        @ParamName("dict") @Editor(`type` = EditorType.DICT_EDITOR) dictValue: Option[Any],
        @ParamName("tabularData") @Editor(`type` = EditorType.TYPED_TABULAR_DATA_EDITOR) tabularDataValue: Option[Any],
    ) =
      Future.successful(jsonValue orElse jsonTemplateValue orElse dictValue orElse tabularDataValue)

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

  class FailingForBlankCustomValidator extends CustomCompileTimeParameterValidator {
    override val name: String = "failingForBlankCustomValidator"

    override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
        implicit nodeId: NodeId
    ): Validated[PartSubGraphCompilationError, Unit] =
      value match {
        case Some(s: String) if s.isBlank =>
          Invalid(
            ProcessCompilationError.CustomParameterValidationError(
              "Value must not be blank",
              "Please provide a non-blank value",
              paramName,
              nodeId
            )
          )
        case _ => Valid(())
      }

  }

  object CustomValidatorTypesService extends Service {

    @MethodToInvoke(returnType = classOf[String])
    def invoke(
        @ParamName("expression") @CustomValidator(classOf[FailingForBlankCustomValidator]) expr: String
    ): Future[String] = Future.successful(expr)

  }

  class DualNotBlankValidator extends CompileTimeParameterValidator with RuntimeParameterValidator {

    override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
        implicit nodeId: NodeId
    ): Validated[PartSubGraphCompilationError, Unit] =
      value match {
        case Some(s: String) if s.isBlank =>
          Invalid(
            ProcessCompilationError.CustomParameterValidationError(
              "Value must not be blank",
              "Please provide a non-blank value",
              paramName,
              nodeId
            )
          )
        case _ => Valid(())
      }

    override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
        implicit nodeId: NodeId
    ): Validated[ParameterRuntimeValidationError, Unit] =
      value match {
        case s: String if !s.isBlank => Valid(())
        case _ =>
          Invalid(ParameterRuntimeValidationError(paramName.value, s"Parameter '${paramName.value}' must not be blank"))
      }

  }

  object DualNotBlankTypesService extends EagerServiceWithStaticParametersAndReturnType {

    private val dualValidator = new DualNotBlankValidator

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("expression"))
        .copy(isLazyParameter = true, validators = List(dualValidator))
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
    ): Future[AnyRef] =
      Future.successful(params(ParameterName("expression")).asInstanceOf[AnyRef])

  }

  object NotNullTypesService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("expression"))
        .copy(isLazyParameter = true, validators = List(NotNullParameterValidator))
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
    ): Future[AnyRef] =
      Future.successful(params(ParameterName("expression")).asInstanceOf[AnyRef])

  }

  object NullSensitiveService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("expression"))
        .copy(isLazyParameter = true, validators = List(NotNullParameterValidator))
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
    ): Future[AnyRef] =
      Future.successful(
        Objects.requireNonNull(params(ParameterName("expression")), "expression must not be null").asInstanceOf[AnyRef]
      )

  }

  object NotBlankRuntimeValidator extends RuntimeParameterValidator {

    override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
        implicit nodeId: NodeId
    ): Validated[ParameterRuntimeValidationError, Unit] =
      value match {
        case s: String if !s.isBlank => Valid(())
        case _ =>
          Invalid(ParameterRuntimeValidationError(paramName.value, s"Parameter '${paramName.value}' must not be blank"))
      }

  }

  object NotBlankRuntimeTypesService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("expression"))
        .copy(isLazyParameter = true, validators = List(NotBlankRuntimeValidator))
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
    ): Future[AnyRef] =
      Future.successful(params(ParameterName("expression")).asInstanceOf[AnyRef])

  }

  private class FixedValueExpression(value: String) extends CompiledExpression {
    override val language: Language                                      = Language.Spel
    override val original: String                                        = s"'$value'"
    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = value.asInstanceOf[T]
  }

  object TransactionSource extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke(returnType = classOf[Transaction])
    def create(): api.process.Source = new api.process.Source {}

  }

  object WithExplicitDefinitionService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(Parameter[Long](ParameterName("param1")))

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(eagerParameters: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
    ): Future[AnyRef] = {
      Future.successful(eagerParameters.head._2.toString)
    }

  }

  object ServiceUsingSpelTemplate extends EagerServiceWithStaticParametersAndReturnType {

    private val spelTemplateParameterName = ParameterName("template")

    private val spelTemplateParameter = Parameter
      .optional[String](spelTemplateParameterName)
      .copy(isLazyParameter = true, editors = List(SpelTemplateParameterEditor))

    override def parameters: List[Parameter] = List(spelTemplateParameter)

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
    ): Future[AnyRef] = {
      Future.successful(params(spelTemplateParameterName).asInstanceOf[String])
    }

  }

  object DictParameterEditorService extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("param")).copy(
        editors = List(DictParameterEditor("someDictId"))
      )
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(eagerParameters: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        context: Context,
        metaData: MetaData,
        componentUseContext: ComponentUseContext
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
          componentUseContext: ComponentUseContext,
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
                componentUseContext: ComponentUseContext,
            ): Future[AnyRef] = {
              Future.successful(lazyOne.evaluate(context))
            }
          }
        }
      )

  }

  object DynamicEagerService extends EagerService with SingleInputDynamicComponent {

    override type Implementation = ServiceInvoker

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
            componentUseContext: ComponentUseContext,
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
