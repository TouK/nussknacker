package pl.touk.nussknacker.engine.benchmarks.interpreter

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Scope, State}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.spel.Implicits._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/*
[info] ManyParamsInterpreterBenchmark.benchmarkAsync  thrpt    8   41268.659 ±  1006.517  ops/s
[info] ManyParamsInterpreterBenchmark.benchmarkSync   thrpt    8  120031.050 ± 12579.394  ops/s
 */
@State(Scope.Thread)
class ManyParamsInterpreterBenchmark {

  private val process: EspProcess = EspProcessBuilder
    .id("t1")
    .exceptionHandlerNoParams()
    .source("source", "source")
    .enricher("e1", "out", "service", (1 to 20).map(i => s"p$i" -> ("''": Expression)): _*)
    .sink("sink", "#out", "sink")

  private def prepareInterpreter(executionContext: ExecutionContext) = {
    val setup = new InterpreterSetup[String].sourceInterpretation(process, Map("service" -> new ManyParamsService(executionContext)), Nil)
    (ctx: Context) => setup(ctx, executionContext)
  }

  private val interpreterSync = prepareInterpreter(SynchronousExecutionContext.create())
  private val interpreterAsync = prepareInterpreter(ExecutionContext.Implicits.global)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkSync(): AnyRef = {
    Await.result(interpreterSync(Context("")), 1 second)
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def benchmarkAsync(): AnyRef = {
    Await.result(interpreterAsync(Context("")), 1 second)
  }



}

object Test extends App {

  private val ec = SynchronousExecutionContext.create()

  private val process: EspProcess = EspProcessBuilder
    .id("t1")
    .exceptionHandlerNoParams()
    .source("source", "source")
    .enricher("e1", "out", "service", (1 to 20).map(i => s"p$i" -> ("''": Expression)): _*)
    .sink("sink", "#out", "sink")
  private val interpreter = new InterpreterSetup[String].sourceInterpretation(process, Map("service" -> new ManyParamsService(ec)), Nil)

  var i = 0
  val count = 100 * 1000
  val start = System.currentTimeMillis()

  while (i<count) {
    i += 1
    Await.result(interpreter(Context(""), ec), 1 second)
    if (i % 1000 == 0) {
      println(s"Running $i")
    }
  }
  println(s"throughput ${(count * 1000)/(System.currentTimeMillis() - start)}/s")

}



class ManyParamsService(expectedEc: ExecutionContext) extends Service {

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
    if (ec != expectedEc) {
      Future.failed(new IllegalArgumentException("Should be normal EC..."))
    } else {
      Future.successful(s1)
    }
  }

}
