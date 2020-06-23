package pl.touk.nussknacker.engine.benchmarks.interpreter

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.benchmarks.interpreter.ManualTest.count
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/*
  many + sync => 13us
  one + async => 12us
  one + sync => 1us
  many + async => 37us (???)

 */
object ManualTest extends App {

  private val ec = ExecutionContext.global//SynchronousExecutionContext.create()
  //private val ec = SynchronousExecutionContext.create()

  private val interpreter: Context => Future[_] = manyParamInterpreter(ec)

  val count = 1000 * 1000
  run(interpreter, count)
  println("warmup finished")
  run(interpreter, count)

  private def run(interpreter: Context => Future[_], count: Long): Unit = {
    val start = System.currentTimeMillis()
    var i = 0
    while (i<count) {
      i += 1
      val fut = interpreter(Context(""))
      if (!fut.isCompleted) {
        Await.result(fut, 1 second)
      }
      if (i % 100000 == 0) {
        println(s"Running $i")
      }
    }
    val time = System.currentTimeMillis() - start
    println(s"throughput ${(count * 1000)/time}/s, avg: ${time * 1000/count}")
  }

  private def manyParamInterpreter(ec: ExecutionContext) = {
    val manyParamProcess: EspProcess = EspProcessBuilder
      .id("t1")
      .exceptionHandlerNoParams()
      .source("source", "source")
      .enricher("e1", "out", "service", (1 to 20).map(i => s"p$i" -> ("''": Expression)): _*)
      .sink("sink", "#out", "sink")
    val setup = new InterpreterSetup[String].sourceInterpretation(manyParamProcess, Map("service" -> new ManyParamsService(ec)), Nil)
    (ctx: Context) => setup(ctx, ec)
  }

  private def oneParamInterpreter(ec: ExecutionContext) = {
    val oneParamProcess: EspProcess = EspProcessBuilder
      .id("t1")
      .exceptionHandlerNoParams()
      .source("source", "source")
      .enricher("e1", "out", "service", "p1" -> "''")
      .sink("sink", "#out", "sink")
    val oneParamInterpreter = new InterpreterSetup[String].sourceInterpretation(oneParamProcess, Map("service" -> OneParamService), Nil)
    (ctx: Context) => oneParamInterpreter(ctx, ec)
  }
}