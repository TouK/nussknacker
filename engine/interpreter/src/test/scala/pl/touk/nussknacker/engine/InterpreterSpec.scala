package pl.touk.nussknacker.engine

import java.util.concurrent.Executor

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.InterpreterSpec._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.lazyy.UsingLazyValues
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.{Service, _}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.PartSubGraphCompilerBase.CompiledNode
import pl.touk.nussknacker.engine.compile.{PartSubGraphCompiler, SubprocessResolver, ValidationContext}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ServiceDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.split.ProcessSplitter
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.splittedgraph.part.SinkPart
import pl.touk.nussknacker.engine.splittedgraph.splittednode
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.util.LoggingListener

import scala.beans.BeanProperty
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class InterpreterSpec extends FlatSpec with Matchers {

  import spel.Implicits._
  import pl.touk.nussknacker.engine.util.Implicits._

  implicit val synchronousExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable) = task.run()
  })

  def listenersDef(listener: Option[ProcessListener] = None): Seq[ProcessListener] =
    listener.toSeq :+ LoggingListener

  val servicesDef = Map(
    "accountService" -> AccountService,
    "dictService" ->  NameDictService
  )

  val sourceFactories = Map(
    "transaction-source" -> ObjectDefinition(List.empty, classOf[Transaction], List())
  )

  def interpretTransaction(node: SourceNode, transaction: Transaction, listeners: Seq[ProcessListener] = listenersDef(), services: Map[String, Service] = servicesDef) = {
    AccountService.clear()
    NameDictService.clear()
    val metaData = MetaData("process1", StreamMetaData())
    val process = EspProcess(metaData, ExceptionHandlerRef(List.empty), node)
    val splitted = ProcessSplitter.split(process)
    val servicesDefs = services.mapValuesNow { service => ObjectWithMethodDef(WithCategories(service), ServiceDefinitionExtractor) }
    val interpreter = Interpreter(servicesDefs, Map(), listeners, true)
    val typesInformation = EspTypeUtils.clazzAndItsChildrenDefinition((servicesDef.values.map(_.getClass) ++ sourceFactories.values.map(c => Class.forName(c.returnType.refClazzName))).toList)
    val compiledNode = compile(servicesDefs, splitted.source.node, ValidationContext(typesInformation = typesInformation, variables = Map(Interpreter.InputParamName -> ClazzRef(classOf[Transaction]))))
    val initialCtx = Context("abc").withVariable(Interpreter.InputParamName, transaction)
    val resultBeforeSink = Await.result(interpreter.interpret(compiledNode.node, InterpreterMode.Traverse, process.metaData, initialCtx), 10 seconds) match {
      case Left(result) => result
      case Right(exceptionInfo) => throw exceptionInfo.throwable
    }

    resultBeforeSink.reference match {
      case NextPartReference(nextPartId) =>
        val sink = splitted.source.nextParts.collectFirst {
          case sink: SinkPart if sink.id == nextPartId =>
            sink.node
        }.get
        Await.result(interpreter.interpret(compile(servicesDefs, sink,
          compiledNode.ctx(nextPartId)).node, InterpreterMode.Traverse, metaData, resultBeforeSink.finalContext), 10 seconds).left.get.output
      case _: EndReference =>
        resultBeforeSink.output
      case _: DeadEndReference =>
        throw new IllegalStateException("Shouldn't happen")
    }
  }

  def compile(servicesDefs: Map[String, ObjectWithMethodDef], node: splittednode.SplittedNode[_], ctx: ValidationContext): CompiledNode = {
    PartSubGraphCompiler.default(servicesDefs, Map.empty, getClass.getClassLoader, ConfigFactory.empty()).compileWithoutContextValidation(node) match {
      case Valid(c) => c
      case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
    }
  }

  it should "finish with returned value" in {
    val process = GraphBuilder.source("start", "transaction-source")
      .sink("end", "#input.msisdn", "")

    interpretTransaction(process, Transaction(msisdn = "125")) should equal ("125")
  }

  it should "filter out based on expression" in {

    val falseEnd = GraphBuilder
      .sink("falseEnd", "'d2'", "")

    val process = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .sink("end", "'d1'", "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("d1")
    interpretTransaction(process, Transaction(accountId = "122")) should equal ("d2")

  }

  it should "ignore disabled filters" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "false", disabled = Some(true))
      .sink("end", "'d1'", "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("d1")
  }

  it should "ignore disabled processors" in {

    var nodes = List[Any]()

    val services = Map("service" ->
      new Service {
        def invoke(@ParamName("id") id: Any)(implicit ec: ExecutionContext) = Future.successful(nodes = id::nodes)
      }
    )

    val process = SourceNode(Source("start", SourceRef("transaction-source", List())),
      OneOutputSubsequentNode(Processor("disabled", ServiceRef("service", List(Parameter("id", Expression("spel", "'disabled'")))), Some(true)),
      OneOutputSubsequentNode(Processor("enabled", ServiceRef("service", List(Parameter("id", Expression("spel", "'enabled'")))), Some(false)),
        EndingNode(Processor("disabledEnd", ServiceRef("service", List(Parameter("id", Expression("spel", "'disabledEnd'")))), Some(true))
      )
    )))
    interpretTransaction(process, Transaction(), Seq.empty, services)

    nodes shouldBe List("enabled")
  }

  it should "invoke processors" in {

    val accountId = "333"
    var result : Any = ""

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("process", "transactionService", "id" -> "#input.accountId")
      .sink("end", "")

    val services = Map("transactionService" ->
      new Service {
        def invoke(@ParamName("id") id: Any)
                  (implicit ec: ExecutionContext) = Future(result = id)
      }
    )

    interpretTransaction(process, Transaction(accountId = accountId), Seq.empty, services)

    result should equal (accountId)

  }

  it should "build node graph ended-up with processor" in {

    val accountId = "333"
    var result : Any = ""

    val process =
      GraphBuilder
        .source("start", "transaction-source")
        .processorEnd("process", "transactionService", "id" -> "#input.accountId")

    val services = Map("transactionService" ->
      new Service {
        def invoke(@ParamName("id") id: Any)
                  (implicit ec: ExecutionContext) = Future(result = id)
      }
    )

    interpretTransaction(process, Transaction(accountId = accountId), Seq.empty, services)

    result should equal (accountId)

  }

  it should "enrich context" in {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .enricher("filter", "account", "accountService", "id" -> "#input.accountId")
      .sink("end", "#account.name", "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("zielonka")
  }

  it should "build variable" in {
    val process = GraphBuilder.source("startVB", "transaction-source")
      .buildVariable("buildVar", "fooVar", "accountId" -> "#input.accountId")
      .sink("end", "#fooVar['accountId']": Expression, "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("123")
  }

  it should "choose based on expression" in {
    val process = GraphBuilder
      .source("start", "transaction-source")
      .switch("switch", "#input.msisdn", "msisdn",
        GraphBuilder.sink("e3end", "'e3'", ""),
        Case("#msisdn == '123'", GraphBuilder.sink("e1", "'e1'", "")),
        Case("#msisdn == '124'", GraphBuilder.sink("e2", "'e2'", "")))

    interpretTransaction(process, Transaction(msisdn = "123")) should equal ("e1")
    interpretTransaction(process, Transaction(msisdn = "124")) should equal ("e2")
    interpretTransaction(process, Transaction(msisdn = "125")) should equal ("e3")

  }

  it should "lazy enrich context" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
        .buildVariable("bv1", "foo", "f1" -> "#input.account.name", "f2" -> "#input.account.name")
        .buildVariable("bv1", "bar", "f1" -> "#input.account.name")
        .sink("end", "#input.account.name", typ = "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("zielonka")
    AccountService.invocations shouldEqual 1
  }

  it should "lazy enrich nested value" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "foo", "f1" -> "#input.account.translatedName", "f2" -> "#input.account.translatedName")
      .buildVariable("bv1", "bar", "f1" -> "#input.account.translatedName")
      .sink("end", "#input.account.translatedName", typ = "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("translatedzielonka")
    AccountService.invocations shouldEqual 1
    NameDictService.invocations shouldEqual 1
  }

  it should "lazy enrich multiple values with the same service" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "foo", "f1" -> "#input.account.translatedName", "f2" -> "#input.account.translatedName")
      .buildVariable("bv1", "bar", "f1" -> "#input.account.translatedName")
      .sink("end", "#input.account.translatedName2", typ = "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("translatedbordo")
    AccountService.invocations shouldEqual 1
    NameDictService.invocations shouldEqual 2
  }

  it should "lazy enrich context with dependent fields" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .buildVariable("bv1", "bar", "f1" -> "#input.dictAccount.name")
      .sink("end", "#input.dictAccount.name", typ = "")

    interpretTransaction(process, Transaction(accountId = "123")) should equal ("zielonka")
    AccountService.invocations shouldEqual 1
    NameDictService.invocations shouldEqual 1
  }


  it should "invoke listeners" in {

    var nodeResults = List[String]()

    var serviceResults = Map[String, Any]()

    val listener = new ProcessListener {

      override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData, mode: InterpreterMode): Unit = {
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
      .enricher("enrich", "account", "accountService", "id" ->  "#input.accountId")
      .sink("end", "#account.name", "")

    val falseEnd = GraphBuilder
      .sink("falseEnd", "'d2'", "")

    val process2 = GraphBuilder
      .source("start", "transaction-source")
      .filter("filter", "#input.accountId == '123'", falseEnd)
      .sink("end", "'d1'", "")

    interpretTransaction(process1, Transaction(), listenersDef(Some(listener)))
    nodeResults should equal (List("start", "enrich", "end"))
    serviceResults should equal (Map("accountService" -> Success(Account(marketingAgreement1 = false, "zielonka", "bordo"))))

    nodeResults = List()
    interpretTransaction(process2, Transaction(), listenersDef(Some(listener)))
    nodeResults should equal (List("start", "filter", "end"))

    nodeResults = List()
    interpretTransaction(process2, Transaction(accountId = "333"), listenersDef(Some(listener)))
    nodeResults should equal (List("start", "filter", "falseEnd"))


  }

  it should "handle subprocess" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("sub", "subProcess1", "output", "param" -> "#input.accountId")

      .sink("sink", "'result'", "sink1"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink1", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))))

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    val resolvedValidated = resolved.toOption.get.root

    interpretTransaction(resolvedValidated, Transaction(accountId = "333"), List()) shouldBe "deadEnd"

    interpretTransaction(resolvedValidated, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  it should "handle subprocess with two occurrences" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("first", "subProcess1", "output", "param" -> "#input.accountId")
      .subprocessOneOut("second", "subProcess1", "output", "param" -> "#input.msisdn")

      .sink("sink", "'result'", "sink1"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink1", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))))


    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    val resolvedValidated = resolved.toOption.get.root

    interpretTransaction(resolvedValidated, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretTransaction(resolvedValidated, Transaction(accountId = "a"), List()) shouldBe "deadEnd"
    interpretTransaction(resolvedValidated, Transaction(msisdn = "a"), List()) shouldBe "deadEnd"
    interpretTransaction(resolvedValidated, Transaction(msisdn = "a", accountId = "a"), List()) shouldBe "result"

  }


  it should "handle nested subprocess" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("first", "subProcess2", "output", "param" -> "#input.accountId")

      .sink("sink", "'result'", "sink1"))

    val subprocess =  CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink1", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))))

    val nested =  CanonicalProcess(MetaData("subProcess2", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.Subprocess(SubprocessInput("sub2",
        SubprocessRef("subProcess1", List(Parameter("param", "#param")))), Map("output" -> List(FlatNode(SubprocessOutputDefinition("sub2Out", "output"))))))
    )

    val resolved = SubprocessResolver(Set(subprocess, nested)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    val resolvedValidated = resolved.toOption.get.root

    interpretTransaction(resolvedValidated, Transaction(accountId = "333"), List()) shouldBe "deadEnd"
    interpretTransaction(resolvedValidated, Transaction(accountId = "a"), List()) shouldBe "result"
  }

  it should "handle subprocess with more than one output" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocess("sub", "subProcess1", List("param" -> "#input.accountId"), Map(
        "output1" -> GraphBuilder.sink("sink", "'result1'", "sink1"),
        "output2" -> GraphBuilder.sink("sink2", "'result2'", "sink1")
      )))


    val subprocess =  CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.SwitchNode(Switch("f1", "#param", "switchParam"),
        List(canonicalnode.Case("#switchParam == 'a'", List(FlatNode(SubprocessOutputDefinition("out1", "output1")))),
          canonicalnode.Case("#switchParam == 'b'", List(FlatNode(SubprocessOutputDefinition("out2", "output2"))))
        ), List())))

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    val resolvedValidated = resolved.toOption.get.root

    interpretTransaction(resolvedValidated, Transaction(accountId = "a"), List()) shouldBe "result1"

    interpretTransaction(resolvedValidated, Transaction(accountId = "b"), List()) shouldBe "result2"
  }

  it should "handle subprocess at end" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessEnd("sub", "subProcess1", "param" -> "#input.accountId"))


    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FlatNode(Sink("result", SinkRef("sink1", List()), Some("'result'")))))

    val resolved = SubprocessResolver(Set(subprocess)).resolve(process).andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    val resolvedValidated = resolved.toOption.get.root

    interpretTransaction(resolvedValidated, Transaction(accountId = "a"), List()) shouldBe "result"

  }

  it should "recognize exception thrown from service as direct exception" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "false")
      .sink("end", "'d1'", "")

    intercept[CustomException] {
      interpretTransaction(process, Transaction(accountId = "123"), services = Map("p1" -> new ThrowingService))
    }.getMessage shouldBe "Fail?"
  }

  it should "recognize exception thrown from service as failed future" in {

    val process = GraphBuilder
      .source("start", "transaction-source")
      .processor("processor", "p1", "failFuture" -> "true")
      .sink("end", "'d1'", "")

    intercept[CustomException] {
      interpretTransaction(process, Transaction(accountId = "123"), services = Map("p1" -> new ThrowingService))
    }.getMessage shouldBe "Fail?"
  }

}

class ThrowingService extends Service {
  @MethodToInvoke
  def invoke(@ParamName("failFuture") failFuture: Boolean) : Future[Void] = {
    if (failFuture) Future.failed(new CustomException("Fail?")) else throw new CustomException("Fail?")
  }
}

class CustomException(message: String) extends Exception(message)

object InterpreterSpec {

  case class Transaction(@BeanProperty msisdn: String = "123",
                         @BeanProperty accountId: String = "123") extends UsingLazyValues {

    val account = lazyValue[Account]("accountService", "id" -> accountId)

    val dictAccount =
      for {
        translatedId <- lazyValue[String]("dictService", "name" -> accountId)
        _ <- lazyValue[String] ("dictService", "name" -> accountId) // invoked twice to check caching
        account <- lazyValue[Account]("accountService", "id" -> translatedId)
      } yield account
  }

  case class Account(@BeanProperty marketingAgreement1: Boolean, @BeanProperty name: String, @BeanProperty name2: String) extends UsingLazyValues {

    val translatedName = lazyValue[String]("dictService", "name" -> name)

    val translatedName2 = lazyValue[String]("dictService", "name" -> name2)

  }

  object AccountService extends Service {

    def invoke(@ParamName("id") id: String)
              (implicit ec: ExecutionContext) = {
      invocations += 1
      Future(Account(marketingAgreement1 = false, "zielonka", "bordo"))
    }

    var invocations = 0

    def clear(): Unit = {
      invocations = 0
    }

  }

  object NameDictService extends Service {

    def invoke(@ParamName("name") name: String) = {
      invocations += 1
      Future.successful("translated" + name)
    }

    var invocations = 0

    def clear(): Unit = {
      invocations = 0
    }
  }

}