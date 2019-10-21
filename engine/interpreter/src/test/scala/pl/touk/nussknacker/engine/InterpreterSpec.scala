package pl.touk.nussknacker.engine

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FunSuite, Matchers}
import org.springframework.expression.spel.standard.SpelExpression
import pl.touk.nussknacker.engine.InterpreterSpec._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{ServiceWithExplicitMethod, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, ExpressionParser, TypedExpression, ValueWithLazyContext}
import pl.touk.nussknacker.engine.api.lazyy.{LazyValuesProvider, UsingLazyValues}
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, LanguageConfiguration, ProcessConfigCreator, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, typing}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Service, _}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.part.{ProcessPart, SinkPart}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.{LoggingListener, SynchronousExecutionContext}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class InterpreterSpec extends FunSuite with Matchers {

  import pl.touk.nussknacker.engine.util.Implicits._
  import spel.Implicits._

  val servicesDef = Map(
    "accountService" -> AccountService,
    "dictService" -> NameDictService,
    "spelNodeService" -> SpelNodeService,
    "withExplicitMethod" -> WithExplicitDefinitionService
  )

  def listenersDef(listener: Option[ProcessListener] = None): Seq[ProcessListener] =
    listener.toSeq :+ LoggingListener


  private def interpretProcess(process: ValidatedNel[_, EspProcess], transaction: Transaction, listeners: Seq[ProcessListener] = listenersDef(), services: Map[String, Service] = servicesDef): Any = {
    interpretSource(process.toOption.get.roots.head, transaction, listeners, services)
  }

  private def interpretSource(node: SourceNode, transaction: Transaction,
                              listeners: Seq[ProcessListener] = listenersDef(),
                              services: Map[String, Service] = servicesDef): Any = {
    import SynchronousExecutionContext.ctx
    AccountService.clear()
    NameDictService.clear()

    val metaData = MetaData("process1", StreamMetaData())
    val process = EspProcess(metaData, ExceptionHandlerRef(List.empty), NonEmptyList.of(node))

    val compiledProcess = compile(services, process, listeners)
    val interpreter = compiledProcess.interpreter
    val parts = compiledProcess.parts

    def compileNode(part: ProcessPart) =
      failOnErrors(compiledProcess.subPartCompiler.compile(part.node, part.validationContext).result)

    val initialCtx = Context("abc").withVariable(Interpreter.InputParamName, transaction)

    val resultBeforeSink = Await.result(interpreter.interpret(compileNode(parts.sources.head), process.metaData, initialCtx), 10 seconds) match {
      case Left(result) => result
      case Right(exceptionInfo) => throw exceptionInfo.throwable
    }

    resultBeforeSink.head.reference match {
      case NextPartReference(nextPartId) =>
        val sink = parts.sources.head.nextParts.collectFirst {
          case sink: SinkPart if sink.id == nextPartId => sink
        }.get
        Await.result(interpreter.interpret(compileNode(sink), metaData, resultBeforeSink.head.finalContext), 10 seconds).left.get.head.output
      case _: EndReference =>
        resultBeforeSink.head.output
      case _: DeadEndReference =>
        throw new IllegalStateException("Shouldn't happen")
      case _: JoinReference =>
        throw new IllegalStateException("Shouldn't happen")
    }
  }

  def compile(servicesToUse: Map[String, Service], process: EspProcess, listeners: Seq[ProcessListener]): CompiledProcess = {

    val configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator {

      override def services(config: Config): Map[String, WithCategories[Service]] = servicesToUse.mapValuesNow(WithCategories(_))

      override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] =
        Map("transaction-source" -> WithCategories(TransactionSource))

      override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]]
        = Map("dummySink" -> WithCategories(SinkFactory.noParam(new pl.touk.nussknacker.engine.api.process.Sink {
        override def testDataOutput: Option[(Any) => String] = None
      })))

      override def expressionConfig(config: Config): ExpressionConfig = super.expressionConfig(config)
        .copy(languages = LanguageConfiguration(List(LiteralExpressionParser)))
    }

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, ConfigFactory.empty())

    failOnErrors(CompiledProcess.compile(process, definitions, listeners, getClass.getClassLoader))
  }

  private def failOnErrors[T](obj: ValidatedNel[ProcessCompilationError, T]): T = obj match {
    case Valid(c) => c
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  test("finish with returned value") {
    val process = GraphBuilder.source("start", "transaction-source")
      .sink("end", "#input.msisdn", "dummySink")

    interpretSource(process, Transaction(msisdn = "125")) should equal("125")
  }

  test("filter out based on expression") {

    val falseEnd = GraphBuilder
      .sink("falseEnd", "'d2'", "dummySink")

    val process = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .sink("end", "'d1'", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("d1")
    interpretSource(process, Transaction(accountId = "122")) should equal("d2")

  }

  test("be able to use SpelExpressionRepr") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("customNode", "rawExpression", "spelNodeService", "expression" -> "#input.accountId == '11' ? 22 : 33")
      .sink("end", "#rawExpression", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("#input.accountId == '11' ? 22 : 33 - Ternary")

  }

  test("ignore disabled filters") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "false", disabled = Some(true))
      .sink("end", "'d1'", "dummySink")

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

  test("enrich context") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("filter", "account", "accountService", "id" -> "#input.accountId")
      .sink("end", "#account.name", "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("zielonka")
  }

  test("build variable") {
    val process = GraphBuilder
      .source("startVB", "transaction-source")
      .buildVariable("buildVar", "fooVar", "accountId" -> "#input.accountId")
      .sink("end", "#fooVar['accountId']": Expression, "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("123")
  }

  test("choose based on expression") {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .switch("switch", "#input.msisdn", "msisdn",
        GraphBuilder.sink("e3end", "'e3'", "dummySink"),
        Case("#msisdn == '123'", GraphBuilder.sink("e1", "'e1'", "dummySink")),
        Case("#msisdn == '124'", GraphBuilder.sink("e2", "'e2'", "dummySink")))

    interpretSource(process, Transaction(msisdn = "123")) should equal("e1")
    interpretSource(process, Transaction(msisdn = "124")) should equal("e2")
    interpretSource(process, Transaction(msisdn = "125")) should equal("e3")

  }

  test("lazy enrich context") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "foo", "f1" -> "#input.account.name", "f2" -> "#input.account.name")
      .buildVariable("bv2", "bar", "f1" -> "#input.account.name")
      .sink("end", "#input.account.name", typ = "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("zielonka")
    AccountService.invocations shouldEqual 1
  }

  test("lazy enrich nested value") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "foo", "f1" -> "#input.account.translatedName", "f2" -> "#input.account.translatedName")
      .buildVariable("bv2", "bar", "f1" -> "#input.account.translatedName")
      .sink("end", "#input.account.translatedName", typ = "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("translatedzielonka")
    AccountService.invocations shouldEqual 1
    NameDictService.invocations shouldEqual 1
  }

  test("lazy enrich multiple values with the same service") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "foo", "f1" -> "#input.account.translatedName", "f2" -> "#input.account.translatedName")
      .buildVariable("bv2", "bar", "f1" -> "#input.account.translatedName")
      .sink("end", "#input.account.translatedName2", typ = "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("translatedbordo")
    AccountService.invocations shouldEqual 1
    NameDictService.invocations shouldEqual 2
  }

  test("lazy enrich context with dependent fields") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "bar", "f1" -> "#input.dictAccount.name")
      .sink("end", "#input.dictAccount.name", typ = "dummySink")

    interpretSource(process, Transaction(accountId = "123")) should equal("zielonka")
    AccountService.invocations shouldEqual 1
    NameDictService.invocations shouldEqual 1
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

      override def exceptionThrown(exceptionInfo: EspExceptionInfo[_ <: Throwable]) = {}
    }

    val process1 = GraphBuilder
      .source("start", "transaction-source")
      .enricher("enrich", "account", "accountService", "id" -> "#input.accountId")
      .sink("end", "#account.name", "dummySink")

    val falseEnd = GraphBuilder
      .sink("falseEnd", "'d2'", "dummySink")

    val process2 = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .sink("end", "'d1'", "dummySink")

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

  test("handle subprocess") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "transaction-source")
      .subprocessOneOut("sub", "subProcess1", "output", "param" -> "#input.accountId")

      .sink("sink", "'result'", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
          List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("dummySink", List()), Some("'deadEnd'"))))
        ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))), None)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"

    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("handle subprocess with two occurrences") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "transaction-source")
      .subprocessOneOut("first", "subProcess1", "output", "param" -> "#input.accountId")
      .subprocessOneOut("second", "subProcess1", "output", "param" -> "#input.msisdn")

      .sink("sink", "'result'", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
          List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("dummySink", List()), Some("'deadEnd'"))))
        ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))), None)


    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(msisdn = "a"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(msisdn = "a", accountId = "a"), List()) shouldBe "result"

  }


  test("handle nested subprocess") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "transaction-source")
      .subprocessOneOut("first", "subProcess2", "output", "param" -> "#input.accountId")

      .sink("sink", "'result'", "dummySink"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
          List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("dummySink", List()), Some("'deadEnd'"))))
        ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))), None)

    val nested = CanonicalProcess(MetaData("subProcess2", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.Subprocess(SubprocessInput("sub2",
          SubprocessRef("subProcess1", List(Parameter("param", "#param")))), Map("output" -> List(FlatNode(SubprocessOutputDefinition("sub2Out", "output")))))), None
    )

    val resolved = SubprocessResolver(Set(subprocess, nested)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  test("handle subprocess with more than one output") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "transaction-source")
      .subprocess("sub", "subProcess1", List("param" -> "#input.accountId"), Map(
        "output1" -> GraphBuilder.sink("sink", "'result1'", "dummySink"),
        "output2" -> GraphBuilder.sink("sink2", "'result2'", "dummySink")
      )))


    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.SwitchNode(Switch("f1", "#param", "switchParam"),
          List(canonicalnode.Case("#switchParam == 'a'", List(FlatNode(SubprocessOutputDefinition("out1", "output1")))),
            canonicalnode.Case("#switchParam == 'b'", List(FlatNode(SubprocessOutputDefinition("out2", "output2"))))
          ), List())), None)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result1"

    interpretProcess(resolved, Transaction(accountId = "b"), List()) shouldBe "result2"
  }

  test("handle subprocess at end") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "transaction-source")
      .subprocessEnd("sub", "subProcess1", "param" -> "#input.accountId"))


    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FlatNode(Sink("result", SinkRef("dummySink", List()), Some("'result'")))), None)

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    interpretProcess(resolved, Transaction(accountId = "a"), List()) shouldBe "result"

  }

  test("recognize exception thrown from service as direct exception") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "false")
      .sink("end", "'d1'", "dummySink")

    intercept[CustomException] {
      interpretSource(process, Transaction(accountId = "123"), services = Map("p1" -> new ThrowingService))
    }.getMessage shouldBe "Fail?"
  }

  test("recognize exception thrown from service as failed future") {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "true")
      .sink("end", "'d1'", "dummySink")

    intercept[CustomException] {
      interpretSource(process, Transaction(accountId = "123"), services = Map("p1" -> new ThrowingService))
    }.getMessage shouldBe "Fail?"
  }

  test("not evaluate disabled filters") {

    val process = GraphBuilder.source("start", "transaction-source")
      .filter("errorFilter", "1/0 == 0", Option(true))
      .sink("end", "#input.msisdn", "dummySink")

    interpretSource(process, Transaction(msisdn = "125")) should equal("125")

  }

  test("invokes services with explicit method") {

    val process = GraphBuilder.source("start", "transaction-source")
      .enricher("ex", "out", "withExplicitMethod", "param1" -> "12333")
      .sink("end", "#out", "dummySink")

    interpretSource(process, Transaction()) should equal("12333")
  }

  test("uses configured expression languages") {
    val testExpression = "literal expression, no need for quotes"

    val process = GraphBuilder.source("start", "transaction-source")
      .sink("end", Expression("literal", testExpression), "dummySink")

    interpretSource(process, Transaction()) should equal(testExpression)
    
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
                         accountId: String = "123") extends UsingLazyValues {

    val account = lazyValue[Account]("accountService", "id" -> accountId)

    val dictAccount =
      for {
        translatedId <- lazyValue[String]("dictService", "name" -> accountId)
        _ <- lazyValue[String]("dictService", "name" -> accountId) // invoked twice to check caching
        account <- lazyValue[Account]("accountService", "id" -> translatedId)
      } yield account
  }

  case class Account(marketingAgreement1: Boolean, name: String, name2: String) extends UsingLazyValues {

    val translatedName = lazyValue[String]("dictService", "name" -> name)

    val translatedName2 = lazyValue[String]("dictService", "name" -> name2)

  }

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

  object TransactionSource extends SourceFactory[Transaction] {

    override def clazz: Class[_] = classOf[Transaction]

    @MethodToInvoke
    def create(): api.process.Source[Transaction] = null

  }

  object WithExplicitDefinitionService extends Service with ServiceWithExplicitMethod {

    override def parameterDefinition: List[api.definition.Parameter]
      = List(api.definition.Parameter("param1", ClazzRef[Long]))


    override def returnType: typing.TypingResult = Typed[String]

    override def invokeService(params: List[AnyRef])(implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, metaData: MetaData): Future[AnyRef] = {
      Future.successful(params.head.asInstanceOf[Long].toString)
    }

  }

  object LiteralExpressionParser extends ExpressionParser {

    case class LiteralExpression(original: String) extends pl.touk.nussknacker.engine.api.expression.Expression {
      override def language: String = languageId

      override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]]
      = Future.successful(ValueWithLazyContext(original.asInstanceOf[T], ctx.lazyContext))
    }

    override def languageId: String = "literal"

    override def parse(original: String, ctx: ValidationContext, expectedType: typing.TypingResult): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
      parseWithoutContextValidation(original).map(TypedExpression(_, Typed[String]))

    override def parseWithoutContextValidation(original: String): Validated[NonEmptyList[ExpressionParseError],
      pl.touk.nussknacker.engine.api.expression.Expression]
      = Valid(LiteralExpression(original))
  }

}