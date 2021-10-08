  package pl.touk.nussknacker.engine.benchmarks.interpreter

import java.util.concurrent.TimeUnit

import cats.effect.IO
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/*
  We don't provide exact results, as they vary significantly between machines
  As the rule of thumb, results below 100k/s should be worrying, for sync interpretation we expect more like 1m/s
 */
@State(Scope.Thread)
class OneParamInterpreterBenchmark {

  private val process: EspProcess = EspProcessBuilder
    .id("t1")
    .exceptionHandlerNoParams()
    .source("source", "source")
    .buildSimpleVariable("v1", "v1", "{a:'', b: 2}")
    .enricher("e1", "out", "service", "p1" -> "''")
    //Uncomment to assess impact of costly variables
    //.buildSimpleVariable("v2", "v2", "{a:'', b: #out, c: {'d','d','ss','aa'}.?[#this.substring(0, 1) == ''] }")
    //.buildSimpleVariable("v3", "v3", "{a:'', b: #out, c: {'d','d','ss','aa'}.?[#this.substring(0, 1) == ''] }")
    .sink("sink", "#out", "sink")

  private val instantlyCompletedFuture = false

  private val service = new OneParamService(instantlyCompletedFuture)

  private val interpreterFuture = new InterpreterSetup[String]
    .sourceInterpretation[Future](process, Map("service" -> service), Nil)(new FutureShape()(SynchronousExecutionContext.ctx))

  private val interpreterIO = new InterpreterSetup[String]
    .sourceInterpretation[IO](process, Map("service" -> service), Nil)

  private val interpreterFutureAsync = new InterpreterSetup[String]
    .sourceInterpretation[Future](process, Map("service" -> service), Nil)(new FutureShape()(ExecutionContext.global))


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkFutureSync(): AnyRef = {
    Await.result(interpreterFuture(Context(""), SynchronousExecutionContext.ctx), 1 second)
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkFutureAsync(): AnyRef = {
    Await.result(interpreterFutureAsync(Context(""), ExecutionContext.Implicits.global), 1 second)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkSyncIO(): AnyRef = {
    interpreterIO(Context(""), SynchronousExecutionContext.ctx).unsafeRunSync()
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkAsyncIO(): AnyRef = {
    interpreterIO(Context(""), ExecutionContext.Implicits.global).unsafeRunSync()
  }



}
class OneParamService(instantlyCompletedFuture: Boolean) extends Service {

  @MethodToInvoke
  def methodToInvoke(@ParamName("p1") s: String)(implicit ec: ExecutionContext): Future[String] = {
    if (instantlyCompletedFuture) Future.successful(s)
    else Future { s }
  }

}
