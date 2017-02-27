package pl.touk.esp.engine.standalone

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import argonaut.{DecodeJson, EncodeJson, Parse}
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.esp.engine.api.test.TestDataParser
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.compile.{PartSubGraphCompilationError, PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.compiledgraph.{CompiledProcessParts, node}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.util.LoggingListener
import pl.touk.esp.engine.{Interpreter, spel}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

class StandaloneProcessInterpreterSpec extends FlatSpec with Matchers {

  import spel.Implicits._
  import scala.concurrent.ExecutionContext.Implicits.global

  it should "run process in request response mode" in {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-source")
//      .filter("filter1", "#input.field1 == 'a'") //fixme dlaczego to nie dziala?
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = Request1("a", "b")
    val config = ConfigFactory.load()
    val creator = new StandaloneProcessConfigCreator
    val interpreter = new StandaloneProcessInterpreter(process)(creator, config)
    interpreter.open()

    val result = interpreter.run(input)

    Await.result(result, Duration(5, TimeUnit.SECONDS)) shouldBe Some("alamakota")
    ProcessorService.invocationsCount.get() shouldBe 1

    interpreter.close()
    ProcessorService.clear()
  }
}