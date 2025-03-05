package pl.touk.nussknacker.engine.benchmarks.interpreter

import cats.effect.IO
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ServiceExecutionContext
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

/*
[info] ManyParamsInterpreterBenchmark.benchmarkAsync  thrpt    8   69367.885 ±   239.049  ops/s
[info] ManyParamsInterpreterBenchmark.benchmarkSync   thrpt    8  248268.590 ± 13458.885  ops/s
 */
@State(Scope.Thread)
class ManyParamsInterpreterBenchmark {

  private val process: CanonicalProcess = ScenarioBuilder
    .streaming("t1")
    .source("source", "source")
    .enricher("e1", "out", "service", (1 to 20).map(i => s"p$i" -> ("''".spel: Expression)): _*)
    .emptySink("sink", "sink")

  private val interpreterIOSyncService = prepareIoInterpreter(
    ServiceExecutionContext(SynchronousExecutionContextAndIORuntime.syncEc)
  )

  private val interpreterIOAsyncService = prepareIoInterpreter(ServiceExecutionContext(ExecutionContext.global))

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkSyncService(): AnyRef = {
    import SynchronousExecutionContextAndIORuntime.syncIoRuntime
    interpreterIOSyncService(Context("")).unsafeRunSync()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkAsyncService(): AnyRef = {
    import SynchronousExecutionContextAndIORuntime.syncIoRuntime
    interpreterIOAsyncService(Context("")).unsafeRunSync()
  }

  private def prepareIoInterpreter(serviceExecutionContext: ServiceExecutionContext) = {
    val setup = new InterpreterSetup[String]
      .sourceInterpretation[IO](
        process,
        List(ComponentDefinition("service", new ManyParamsService(serviceExecutionContext)))
      )
    (ctx: Context) => setup(ctx, serviceExecutionContext)
  }

}

class ManyParamsService(serviceExecutionContext: ServiceExecutionContext) extends Service {

  @MethodToInvoke
  def methodToInvoke(
      @ParamName("p1") s1: String,
      @ParamName("p2") s2: String,
      @ParamName("p3") s3: String,
      @ParamName("p4") s4: String,
      @ParamName("p5") s5: String,
      @ParamName("p6") s6: String,
      @ParamName("p7") s7: String,
      @ParamName("p8") s8: String,
      @ParamName("p9") s9: String,
      @ParamName("p10") s10: String,
      @ParamName("p11") s11: String,
      @ParamName("p12") s12: String,
      @ParamName("p13") s13: String,
      @ParamName("p14") s14: String,
      @ParamName("p15") s15: String,
      @ParamName("p16") s16: String,
      @ParamName("p17") s17: String,
      @ParamName("p18") s18: String,
      @ParamName("p19") s19: String,
      @ParamName("p20") s20: String
  )(implicit ec: ExecutionContext): Future[String] = {
    if (ec != serviceExecutionContext.executionContext) {
      Future.failed(new IllegalArgumentException("Should be normal EC..."))
    } else {
      Future.successful(s1)
    }
  }

}
