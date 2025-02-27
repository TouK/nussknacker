package pl.touk.nussknacker.engine.benchmarks.interpreter

import cats.effect.IO
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ServiceExecutionContext
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/*
  We don't provide exact results, as they vary significantly between machines
  As the rule of thumb, results below 100k/s should be worrying, for sync interpretation we expect more like 1m/s
 */
@State(Scope.Thread)
class OneParamInterpreterBenchmark {

  private val process: CanonicalProcess = ScenarioBuilder
    .streaming("t1")
    .source("source", "source")
    .buildSimpleVariable("v1", "v1", "{a:'', b: 2}".spel)
    .enricher("e1", "out", "service", "p1" -> "''".spel)
    // Uncomment to assess impact of costly variables
    // .buildSimpleVariable("v2", "v2", "{a:'', b: #out, c: {'d','d','ss','aa'}.?[#this.substring(0, 1) == ''] }")
    // .buildSimpleVariable("v3", "v3", "{a:'', b: #out, c: {'d','d','ss','aa'}.?[#this.substring(0, 1) == ''] }")
    .emptySink("sink", "sink")

  private val instantlyCompletedFuture = false

  private val service = new OneParamService(instantlyCompletedFuture)

  private val interpreterFuture = {
    implicit val ec: ExecutionContext = SynchronousExecutionContextAndIORuntime.syncEc
    new InterpreterSetup[String].sourceInterpretation[Future](process, List(ComponentDefinition("service", service)))
  }

  private val interpreterIO =
    new InterpreterSetup[String].sourceInterpretation[IO](process, List(ComponentDefinition("service", service)))

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkFutureSyncService(): AnyRef = {
    Await.result(
      interpreterFuture(Context(""), ServiceExecutionContext(SynchronousExecutionContextAndIORuntime.syncEc)),
      1 second
    )
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkFutureAsyncService(): AnyRef = {
    Await.result(
      interpreterFuture(Context(""), ServiceExecutionContext(ExecutionContext.global)),
      1 second
    )
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkIOSyncService(): AnyRef = {
    import SynchronousExecutionContextAndIORuntime.syncIoRuntime
    interpreterIO(Context(""), ServiceExecutionContext(SynchronousExecutionContextAndIORuntime.syncEc))
      .unsafeRunSync()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkIOAsyncService(): AnyRef = {
    import SynchronousExecutionContextAndIORuntime.syncIoRuntime
    interpreterIO(Context(""), ServiceExecutionContext(ExecutionContext.global))
      .unsafeRunSync()
  }

}

class OneParamService(instantlyCompletedFuture: Boolean) extends Service {

  @MethodToInvoke
  def methodToInvoke(@ParamName("p1") s: String)(implicit ec: ExecutionContext): Future[String] = {
    if (instantlyCompletedFuture) Future.successful(s)
    else Future { s }
  }

}
