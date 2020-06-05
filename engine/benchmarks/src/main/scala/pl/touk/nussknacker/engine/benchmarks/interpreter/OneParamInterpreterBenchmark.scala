package pl.touk.nussknacker.engine.benchmarks.interpreter

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/*
[info] OneParamInterpreterBenchmark.benchmarkAsync  thrpt    8    69390.971 ±   5787.658  ops/s
[info] OneParamInterpreterBenchmark.benchmarkSync   thrpt    8  1203125.849 ± 162578.399  ops/s
 */
@State(Scope.Thread)
class OneParamInterpreterBenchmark {

  private val process: EspProcess = EspProcessBuilder
    .id("t1")
    .exceptionHandlerNoParams()
    .source("source", "source")
    .enricher("e1", "out", "service", "p1" -> "''")
    .sink("sink", "#out", "sink")
  private val interpreter = new InterpreterSetup[String].sourceInterpretation(process, Map("service" -> OneParamService), Nil)


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkSync(): AnyRef = {
    Await.result(interpreter(Context(""), SynchronousExecutionContext.ctx), 1 second)
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkAsync(): AnyRef = {
    Await.result(interpreter(Context(""), ExecutionContext.Implicits.global), 1 second)
  }



}
object OneParamService extends Service {

  @MethodToInvoke
  def methodToInvoke(@ParamName("p1") s: String): Future[String] = {
    Future.successful(s)
  }

}
