package pl.touk.nussknacker.engine.benchmarks.interpreter

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/*
  We don't provide exact results, as they vary significantly between machines
  As the rule of thumb, results below 100k/s should be worrying, for sync interpretation we expect more like 1m/s
 */
@State(Scope.Thread)
class OneParamInterpreterBenchmark {

  private val process: CanonicalProcess = ScenarioBuilder
    .streaming("t1")
    .source("source", "source")
    .buildSimpleVariable("v1", "v1", "{a:'', b: 2}")
    .enricher("e1", "out", "service", "p1" -> "''")
    // Uncomment to assess impact of costly variables
    // .buildSimpleVariable("v2", "v2", "{a:'', b: #out, c: {'d','d','ss','aa'}.?[#this.substring(0, 1) == ''] }")
    // .buildSimpleVariable("v3", "v3", "{a:'', b: #out, c: {'d','d','ss','aa'}.?[#this.substring(0, 1) == ''] }")
    .emptySink("sink", "sink")

  private val instantlyCompletedFuture = false

  private val service = new OneParamService(instantlyCompletedFuture)

  private val interpreterFutureSync = {
    implicit val ec: ExecutionContext = SynchronousExecutionContextAndIORuntime.syncEc
    new InterpreterSetup[String].sourceInterpretation[Future](process, Map("service" -> service), Nil)
  }

  private val interpreterFutureAsync = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    new InterpreterSetup[String].sourceInterpretation[Future](process, Map("service" -> service), Nil)
  }

  private val interpreterIO =
    new InterpreterSetup[String].sourceInterpretation[IO](process, Map("service" -> service), Nil)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkFutureSync(): AnyRef = {
    Await.result(
      interpreterFutureSync(ScenarioProcessingContext(""), SynchronousExecutionContextAndIORuntime.syncEc),
      1 second
    )
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkFutureAsync(): AnyRef = {
    Await.result(interpreterFutureAsync(ScenarioProcessingContext(""), ExecutionContext.Implicits.global), 1 second)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkSyncIO(): AnyRef = {
    interpreterIO(ScenarioProcessingContext(""), SynchronousExecutionContextAndIORuntime.syncEc).unsafeRunSync()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkAsyncIO(): AnyRef = {
    interpreterIO(ScenarioProcessingContext(""), ExecutionContext.Implicits.global).unsafeRunSync()
  }

}

class OneParamService(instantlyCompletedFuture: Boolean) extends Service {

  @MethodToInvoke
  def methodToInvoke(@ParamName("p1") s: String)(implicit ec: ExecutionContext): Future[String] = {
    if (instantlyCompletedFuture) Future.successful(s)
    else Future { s }
  }

}
