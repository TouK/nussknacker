package pl.touk.nussknacker.engine

import java.util.{Collections, Optional}
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.effect.IO
import com.typesafe.config.ConfigFactory

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import org.scalatest.{FunSuite, Matchers}
import org.springframework.expression.spel.standard.SpelExpression
import pl.touk.nussknacker.engine.InterpreterSpec.{DynamicEagerService, _}
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedLazyParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, ParameterWithExtractor}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.expression.{Expression => _, _}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Service, _}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.part.{CustomNodePart, ProcessPart, SinkPart}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.service.{EnricherContextTransformation, ServiceWithStaticParametersAndReturnType}
import pl.touk.nussknacker.engine.util.{LoggingListener, SynchronousExecutionContext}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class InterpreterSpec extends FunSuite with Matchers {

  import pl.touk.nussknacker.engine.util.Implicits._
  import spel.Implicits._

  val resultVariable = "result"

  val servicesDef = Map(
    "accountService" -> AccountService,
    "dictService" -> NameDictService,
    "spelNodeService" -> SpelNodeService,
    "withExplicitMethod" -> WithExplicitDefinitionService,
    "optionTypesService" -> OptionTypesService,
    "optionalTypesService" -> OptionalTypesService,
    "nullableTypesService" -> NullableTypesService,
    "mandatoryTypesService" -> MandatoryTypesService,
    "notBlankTypesService" -> NotBlankTypesService,
    "eagerServiceWithMethod" -> EagerServiceWithMethod,
    "dynamicEagerService" -> DynamicEagerService,
    "eagerServiceWithFixedAdditional" -> EagerServiceWithFixedAdditional
  )

  def listenersDef(listener: Option[ProcessListener] = None): Seq[ProcessListener] =
    listener.toSeq :+ LoggingListener


  private def interpretProcess(process: ValidatedNel[_, EspProcess], transaction: Transaction, listeners: Seq[ProcessListener] = listenersDef(), services: Map[String, Service] = servicesDef): Any = {
    interpretSource(process.toOption.get.roots.head, transaction, listeners, services)
  }

  private def interpretSource(node: SourceNode, transaction: Transaction,
                              listeners: Seq[ProcessListener] = listenersDef(),
                              services: Map[String, Service] = servicesDef,
                              transformers: Map[String, CustomStreamTransformer] = Map()): Any = {
    import SynchronousExecutionContext.ctx
    import Interpreter._

    AccountService.clear()
    NameDictService.clear()

    val metaData = MetaData("process1", StreamMetaData())
    implicit val runMode: RunMode = RunMode.Normal
    val process = EspProcess(metaData, NonEmptyList.of(node))

    val processCompilerData = compile(services, transformers, process, listeners)
    val interpreter = processCompilerData.interpreter
    val parts = failOnErrors(processCompilerData.compile())

    def compileNode(part: ProcessPart) =
      failOnErrors(processCompilerData.subPartCompiler.compile(part.node, part.validationContext)(metaData).result)

    val initialCtx = Context("abc").withVariable(VariableConstants.InputVariableName, transaction)

    val resultBeforeSink = interpreter.interpret[IO](compileNode(parts.sources.head), process.metaData, initialCtx).unsafeRunSync().head match {
      case Left(result) => result
      case Right(exceptionInfo) => throw exceptionInfo.throwable
    }

    resultBeforeSink.reference match {
      case NextPartReference(nextPartId) =>
        val sink = parts.sources.head.nextParts.collectFirst {
          case sink: SinkPart if sink.id == nextPartId => sink
          case endingCustomPart: CustomNodePart if endingCustomPart.id == nextPartId => endingCustomPart
        }.get
        interpreter.interpret(compileNode(sink), metaData, resultBeforeSink.finalContext).unsafeRunSync().head.left.get.finalContext.get(resultVariable).orNull
      case _: EndReference =>
        resultBeforeSink.output
      case _: DeadEndReference =>
        throw new IllegalStateException("Shouldn't happen")
      case _: JoinReference =>
        throw new IllegalStateException("Shouldn't happen")
    }
  }

  def compile(servicesToUse: Map[String, Service], customStreamTransformersToUse: Map[String, CustomStreamTransformer], process: EspProcess, listeners: Seq[ProcessListener]): ProcessCompilerData = {

    val configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator {

      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = customStreamTransformersToUse.mapValuesNow(WithCategories(_))

      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = servicesToUse.mapValuesNow(WithCategories(_))

      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
        Map("transaction-source" -> WithCategories(TransactionSource))

      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]]
      = Map("dummySink" -> WithCategories(SinkFactory.noParam(new pl.touk.nussknacker.engine.api.process.Sink {})))

      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = super.expressionConfig(processObjectDependencies)
        .copy(languages = LanguageConfiguration(List(LiteralExpressionParser)), dynamicPropertyAccessAllowed = true)
    }

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, api.process.ProcessObjectDependencies(ConfigFactory.empty(), ObjectNamingProvider(getClass.getClassLoader)))

    ProcessCompilerData.prepare(process, definitions, listeners, getClass.getClassLoader, ProductionServiceInvocationCollector, RunMode.Normal)(DefaultAsyncInterpretationValueDeterminer.DefaultValue)
  }

  private def failOnErrors[T](obj: ValidatedNel[ProcessCompilationError, T]): T = obj match {
    case Valid(c) => c
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  test("finish with returned value") {
    val process = GraphBuilder.source("start", "transaction-source")
      .buildSimpleVariable("result-end", resultVariable, "#input.msisdn").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(msisdn = "125")) should equal("125")
  }

  test("filter out based on expression") {

    val falseEnd = GraphBuilder
      .buildSimpleVariable("result-falseEnd", resultVariable, "'d2'").emptySink("end-falseEnd", "dummySink")

    val process = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .buildSimpleVariable("result-end", resultVariable, "'d1'").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("d1")
    interpretSource(process, Transaction(accountId = "122")) should equal("d2")

  }

  test("be able to use SpelExpressionRepr") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "spelNodeService", "expression" -> "#input.accountId == '11' ? 22 : 33")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("#input.accountId == '11' ? 22 : 33 - Ternary")
  }

  test("ignore disabled filters") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "false", disabled = Some(true))
      .buildSimpleVariable("result-end", resultVariable, "'d1'").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("d1")
  }

  test("ignore disabled sinks") {
    val process = SourceNode(
      Source("start", SourceRef("transaction-source", List.empty)),
      EndingNode(Sink("end", SinkRef("dummySink", List.empty), isDisabled = Some(true)))
    )

    assert(interpretSource(process, Transaction(accountId = "123")) == null)
  }

  test("ignore disabled processors") {
    var nodes = List[Any]()

    val services = Map("service" ->
      new Service {
        @MethodToInvoke
        def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext) = Future {
          nodes = id :: nodes
        }
      }
    )

    val process = SourceNode(Source("start", SourceRef("transaction-source", List())),
      OneOutputSubsequentNode(Processor("disabled", ServiceRef("service", List(Parameter("id", Expression("spel", "'disabled'")))), Some(true)),
        OneOutputSubsequentNode(Processor("enabled", ServiceRef("service", List(Parameter("id", Expression("spel", "'enabled'")))), Some(false)),
          EndingNode(Processor("disabledEnd", ServiceRef("service", List(Parameter("id", Expression("spel", "'disabledEnd'")))), Some(true))
          )
        )))
    interpretSource(process, Transaction(), Seq.empty, services)

    nodes shouldBe List("enabled")
  }

  test("invoke processors") {
    val accountId = "333"
    var result: Any = ""

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("process", "transactionService", "id" -> "#input.accountId")
      .emptySink("end", "dummySink")

    val services = Map("transactionService" ->
      new Service {
        @MethodToInvoke
        def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext) = Future {
          result = id
        }
      }
    )

    interpretSource(process, Transaction(accountId = accountId), Seq.empty, services)

    result should equal(accountId)
  }

  test("build node graph ended-up with processor") {
    val accountId = "333"
    var result: Any = ""

    val process =
      GraphBuilder
        .source("start", "transaction-source")
        .processorEnd("process", "transactionService", "id" -> "#input.accountId")

    val services = Map("transactionService" ->
      new Service {
        @MethodToInvoke
        def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext) = Future {
          result = id
        }
      }
    )

    interpretSource(process, Transaction(accountId = accountId), Seq.empty, services)

    result should equal(accountId)
  }


  test("build node graph ended-up with custom node") {
    val accountId = "333"
    val process =
      GraphBuilder
        .source("start", "transaction-source")
        .endingCustomNode("process", None, "transactionCustomNode")

    val transformers = Map("transactionCustomNode" ->
      new CustomStreamTransformer {
        @MethodToInvoke
        def create(): Unit = {
        }

        override def canBeEnding: Boolean = true
      }
    )
    interpretSource(process, Transaction(accountId = accountId), Seq.empty, transformers = transformers)
  }

  test("enrich context") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("filter", "account", "accountService", "id" -> "#input.accountId")
      .buildSimpleVariable("result-end", resultVariable, "#account.name").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("zielonka")
  }

  test("build variable") {
    val process = GraphBuilder
      .source("startVB", "transaction-source")
      .buildVariable("buildVar", "fooVar", "accountId" -> "#input.accountId")
      .buildSimpleVariable("result-end", resultVariable, "#fooVar['accountId']": Expression).emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("123")
  }

  test("choose based on expression") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .switch("switch", "#input.msisdn", "msisdn",
        GraphBuilder.buildSimpleVariable("result-e3end", resultVariable, "'e3'").emptySink("end-e3end", "dummySink"),
        Case("#msisdn == '123'", GraphBuilder.buildSimpleVariable("result-e1", resultVariable, "'e1'").emptySink("end-e1", "dummySink")),
        Case("#msisdn == '124'", GraphBuilder.buildSimpleVariable("result-e2", resultVariable, "'e2'").emptySink("end-e2", "dummySink")))

    interpretSource(process, Transaction(msisdn = "123")) should equal("e1")
    interpretSource(process, Transaction(msisdn = "124")) should equal("e2")
    interpretSource(process, Transaction(msisdn = "125")) should equal("e3")

  }

  test("invoke listeners") {

    var nodeResults = List[String]()

    var serviceResults = Map[String, Any]()

    val listener = new ProcessListener {

      override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
        nodeResults = nodeResults :+ nodeId
      }

      override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit = {}

      override def serviceInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, params: Map[String, Any], result: Try[Any]): Unit = {
        serviceResults = serviceResults + (id -> result)
      }

      override def expressionEvaluated(nodeId: String, expressionId: String, expression: String, context: Context, processMetaData: MetaData, result: Any): Unit = {}

      override def sinkInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, param: Any) = {}

      override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]) = {}
    }

    val process1 = GraphBuilder
      .source("start", "transaction-source")
      .enricher("enrich", "account", "accountService", "id" -> "#input.accountId")
      .emptySink("end", "dummySink")

    val falseEnd = GraphBuilder
      .emptySink("falseEnd", "dummySink")

    val process2 = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .emptySink("end", "dummySink")

    interpretSource(process1, Transaction(), listenersDef(Some(listener)))
    nodeResults should equal(List("start", "enrich", "end"))
    serviceResults should equal(Map("accountService" -> Success(Account(marketingAgreement1 = false, "zielonka", "bordo"))))

    nodeResults = List()
    interpretSource(process2, Transaction(), listenersDef(Some(listener)))
    nodeResults should equal(List("start", "filter", "end"))

    nodeResults = List()
    interpretSource(process2, Transaction(accountId = "333"), listenersDef(Some(listener)))
    nodeResults should equal(List("start", "filter", "falseEnd"))


  }

  test("handle fragment") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .source("source", "transaction-source")
      .subprocessOneOut("sub", "subProcess1", "output", "param" -> "#input.accountId")

      .buildSimpleVariable("result-sink", resultVariable, "'result'").emptySink("end-sink", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
          List(FlatNode(Variable("result", resultVariable, "'deadEnd'")), FlatNode(Sink("deadEnd", SinkRef("dummySink", List()))))
        ), FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), List.empty)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"

    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("handle fragment with two occurrences") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .source("source", "transaction-source")
      .subprocessOneOut("first", "subProcess1", "output", "param" -> "#input.accountId")
      .subprocessOneOut("second", "subProcess1", "output", "param" -> "#input.msisdn")
      .buildSimpleVariable("result-sink", resultVariable, "'result'")
      .emptySink("end-sink", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
          List(FlatNode(Variable("result", resultVariable, "'deadEnd'")), FlatNode(Sink("deadEnd", SinkRef("dummySink", List()))))
        ), FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), List.empty)


    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(msisdn = "a"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(msisdn = "a", accountId = "a"), List()) shouldBe "result"

  }


  test("handle nested fragment") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .source("source", "transaction-source")
      .subprocessOneOut("first", "subProcess2", "output", "param" -> "#input.accountId")
      .buildSimpleVariable("result-sink", resultVariable, "'result'")
      .emptySink("end-sink", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
          List(FlatNode(Variable("result", "result", "'deadEnd'")), FlatNode(Sink("deadEnd", SinkRef("dummySink", List()))))
        ), FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), List.empty)

    val nested = CanonicalProcess(MetaData("subProcess2", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.Subprocess(SubprocessInput("sub2",
          SubprocessRef("subProcess1", List(Parameter("param", "#param")))), Map("output" -> List(FlatNode(SubprocessOutputDefinition("sub2Out", "output", List.empty)))))), List.empty
    )

    val resolved = SubprocessResolver(Set(subprocess, nested)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("handle fragment with more than one output") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .source("source", "transaction-source")
      .subprocess("sub", "subProcess1", List("param" -> "#input.accountId"), Map(
        "output1" -> GraphBuilder.buildSimpleVariable("result-sink", resultVariable, "'result1'").emptySink("end-sink", "dummySink"),
        "output2" -> GraphBuilder.buildSimpleVariable("result-sink2", resultVariable, "'result2'").emptySink("end-sink2", "dummySink")
      )))


    val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.SwitchNode(Switch("f1", "#param", "switchParam"),
          List(canonicalnode.Case("#switchParam == 'a'", List(FlatNode(SubprocessOutputDefinition("out1", "output1", List.empty)))),
            canonicalnode.Case("#switchParam == 'b'", List(FlatNode(SubprocessOutputDefinition("out2", "output2", List.empty))))
          ), List())), List.empty)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result1"

    interpretProcess(resolved, Transaction(accountId = "b"), List()) shouldBe "result2"
  }

  test("handle fragment at end") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .source("source", "transaction-source")
      .subprocessEnd("sub", "subProcess1", "param" -> "#input.accountId"))


    val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        FlatNode(Variable("result", "result", "'result'")),
        FlatNode(Sink("end", SinkRef("dummySink", List())))), List.empty)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("interprets fragment output fields") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .source("source", "transaction-source")
      .subprocessOneOut("sub", "subProcess1", "output", "toMultiply" -> "2", "multiplyBy" -> "4")
      .buildSimpleVariable("result-sink", resultVariable, "#output.result.toString").emptySink("end-sink", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
      List(
        FlatNode(SubprocessInputDefinition("start", List(
          SubprocessParameter("toMultiply", SubprocessClazzRef[java.lang.Integer]),
          SubprocessParameter("multiplyBy", SubprocessClazzRef[java.lang.Integer])
        ))),
        FlatNode(SubprocessOutputDefinition(
          "subOutput1", "output", List(Field("result", "#toMultiply * #multiplyBy")))
        )
      ), List.empty)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)
    resolved shouldBe 'valid
    interpretProcess(resolved, Transaction(accountId = "a"), List.empty) shouldBe "8"
  }

  test("recognize exception thrown from service as direct exception") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "false")
      .buildSimpleVariable("result-end", resultVariable, "'d1'").emptySink("end-end", "dummySink")

    intercept[CustomException] {
      interpretSource(process, Transaction(accountId = "123"), services = Map("p1" -> new ThrowingService))
    }.getMessage shouldBe "Fail?"
  }

  test("recognize exception thrown from service as failed future") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "true")
      .buildSimpleVariable("result-end", resultVariable, "'d1'").emptySink("end-end", "dummySink")

    intercept[CustomException] {
      interpretSource(process, Transaction(accountId = "123"), services = Map("p1" -> new ThrowingService))
    }.getMessage shouldBe "Fail?"
  }

  test("not evaluate disabled filters") {

    val process = GraphBuilder.source("start", "transaction-source")
      .filter("errorFilter", "1/0 == 0", Option(true))
      .buildSimpleVariable("result-end", resultVariable, "#input.msisdn").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction(msisdn = "125")) should equal("125")

  }

  test("invokes services with explicit method") {

    val process = GraphBuilder.source("start", "transaction-source")
      .enricher("ex", "out", "withExplicitMethod", "param1" -> "12333")
      .buildSimpleVariable("result-end", resultVariable, "#out").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) should equal("12333")
  }

  test("uses configured expression languages") {
    val testExpression = "literal expression, no need for quotes"

    val process = GraphBuilder.source("start", "transaction-source")
      .buildSimpleVariable("result-end", resultVariable, Expression("literal", testExpression))
      .emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) should equal(testExpression)

  }

  test("accept empty expression for option parameter") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "optionTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) should equal(Option.empty)
  }

  test("accept empty expression for optional parameter") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "optionalTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) should equal(Optional.empty())
  }

  test("accept empty expression for nullable parameter") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "nullableTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()).asInstanceOf[String] shouldBe null
  }

  test("not accept no expression for mandatory parameter") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "mandatoryTypesService", "expression" -> "")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression").emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretSource(process, Transaction())
    }.getMessage shouldBe "Compilation errors: EmptyMandatoryParameter(This field is mandatory and can not be empty,Please fill field for this parameter,expression,customNode)"
  }

  test("not accept blank expression for not blank parameter") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "notBlankTypesService", "expression" -> "''")
      .buildSimpleVariable("result-end", resultVariable, "#rawExpression").emptySink("end-end", "dummySink")

    intercept[IllegalArgumentException] {
      interpretSource(process, Transaction())
    }.getMessage shouldBe "Compilation errors: BlankParameter(This field value is required and can not be blank,Please fill field value for this parameter,expression,customNode)"
  }

  test("use eager service") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "data", "eagerServiceWithMethod",
        "eager" -> s"'${EagerServiceWithMethod.checkEager}'",
        "lazy" -> "{bool: '' == null}")
      .filter("filter", "!#data.bool")
      .buildSimpleVariable("result-end", resultVariable, "#data").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) shouldBe Collections.singletonMap("bool", false)
  }

  test("use dynamic service") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "data", "dynamicEagerService",
        DynamicEagerService.staticParamName -> "'param987'",
        "param987" -> "{bool: '' == null}")
      .filter("filter", "!#data.bool")
      .buildSimpleVariable("result-end", resultVariable, "#data").emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) shouldBe Collections.singletonMap("bool", false)
  }

  test("inject fixed additional variable") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "data", "eagerServiceWithFixedAdditional",
        "param" -> "#helper.value")
      .buildSimpleVariable("result-end", resultVariable, "#data")
      .emptySink("end-end", "dummySink")

    interpretSource(process, Transaction()) shouldBe new Helper().value
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

  case class Transaction(msisdn: String = "123",
                         accountId: String = "123")

  case class Account(marketingAgreement1: Boolean, name: String, name2: String)

  case class LiteralExpressionTypingInfo(typingResult: TypingResult) extends ExpressionTypingInfo

  object AccountService extends Service {

    var invocations = 0

    @MethodToInvoke(returnType = classOf[Account])
    def invoke(@ParamName("id") id: String)
              (implicit ec: ExecutionContext) = {
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

  object TransactionSource extends SourceFactory {

    @MethodToInvoke(returnType = classOf[Transaction])
    def create(): api.process.Source = null

  }

  object WithExplicitDefinitionService extends ServiceWithStaticParametersAndReturnType {

    override def parameters: List[api.definition.Parameter]
    = List(api.definition.Parameter[Long]("param1"))

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                  collector: InvocationCollectors.ServiceInvocationCollector,
                                                  contextId: ContextId,
                                                  metaData: MetaData): Future[AnyRef] = {
      Future.successful(params.head._2.toString)
    }

  }

  object LiteralExpressionParser extends ExpressionParser {

    override def languageId: String = "literal"

    override def parse(original: String, ctx: ValidationContext, expectedType: typing.TypingResult): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
      parseWithoutContextValidation(original, expectedType).map(TypedExpression(_, Typed[String], LiteralExpressionTypingInfo(typing.Unknown)))

    override def parseWithoutContextValidation(original: String, expectedType: TypingResult): Validated[NonEmptyList[ExpressionParseError],
      pl.touk.nussknacker.engine.api.expression.Expression]
    = Valid(LiteralExpression(original))

    case class LiteralExpression(original: String) extends pl.touk.nussknacker.engine.api.expression.Expression {
      override def language: String = languageId

      override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = original.asInstanceOf[T]
    }

  }

  class Helper {
    val value = "injected"
  }

  object EagerServiceWithFixedAdditional extends EagerService {
    @MethodToInvoke(returnType = classOf[String])
    def prepare(@ParamName("param")
                @AdditionalVariables(Array(new AdditionalVariable(name = "helper", clazz = classOf[Helper])))
                param: String): ServiceInvoker = new ServiceInvoker {
      override def invokeService(params: Map[String, Any])
                                (implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector,
                                 contextId: ContextId, runMode: RunMode): Future[Any] = {
        Future.successful(param)
      }
    }
  }

  object EagerServiceWithMethod extends EagerService {

    val checkEager = "@&#%@Q&#"

    @MethodToInvoke
    def prepare(@ParamName("eager") eagerOne: String,
                @ParamName("lazy") lazyOne: LazyParameter[AnyRef],
                @OutputVariableName outputVar: String)(implicit nodeId: NodeId): ContextTransformation =
      EnricherContextTransformation(outputVar, lazyOne.returnType, {
        if (eagerOne != checkEager) throw new IllegalArgumentException("Should be not empty?")
        new ServiceInvoker {
          override def invokeService(params: Map[String, Any])
                                    (implicit ec: ExecutionContext,
                                     collector: InvocationCollectors.ServiceInvocationCollector,
                                     contextId: ContextId,
                                     runMode: RunMode): Future[AnyRef] = {
            Future.successful(params("lazy").asInstanceOf[AnyRef])
          }
        }
    })

  }

  object DynamicEagerService extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {

    override type State = Nothing

    final val staticParamName = "static"

    private val staticParam = ParameterWithExtractor.mandatory[String](staticParamName)

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                      (implicit nodeId: ProcessCompilationError.NodeId): DynamicEagerService.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(List(staticParam.parameter))
      case TransformationStep((`staticParamName`, DefinedEagerParameter(value: String, _)) :: Nil, _) =>
        NextParameters(dynamicParam(value).parameter :: Nil)
      case TransformationStep((`staticParamName`, DefinedEagerParameter(value: String, _)) ::
        (otherName, DefinedLazyParameter(expression)) :: Nil, _) if value == otherName =>
        FinalResults.forValidation(context)(_.withVariable(OutputVariableNameDependency.extract(dependencies), expression.returnType, None))
    }

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue],
                                finalState: Option[Nothing]): ServiceInvoker = {

      val paramName = staticParam.extractValue(params)

      new ServiceInvoker {
        override def invokeService(params: Map[String, Any])
                                  (implicit ec: ExecutionContext,
                                   collector: InvocationCollectors.ServiceInvocationCollector,
                                   contextId: ContextId,
                                   runMode: RunMode): Future[AnyRef] = {
          Future.successful(params(paramName).asInstanceOf[AnyRef])
        }
      }
    }

    private def dynamicParam(name: String) = ParameterWithExtractor.lazyMandatory[AnyRef](name)

    override def nodeDependencies: List[NodeDependency] = Nil

  }

}
