package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.Collections

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.util.Collector
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.graph.node.Source
import pl.touk.esp.engine.graph.{EspProcess, node}
import pl.touk.esp.engine.process.FlinkProcessRegistrar._
import pl.touk.esp.engine.process.util.SynchronousExecutionContext
import pl.touk.esp.engine.traverse.NodesCollector
import pl.touk.esp.engine.{Interpreter, InterpreterConfig, api}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class FlinkProcessRegistrar(interpreterConfig: => InterpreterConfig,
                            sourceFactories: Map[String, SourceFactory],
                            sinkFactories: Map[String, SinkFactory],
                            defaultSink: api.Sink) {

  def register(env: StreamExecutionEnvironment, process: EspProcess, processTimeout: Duration): Unit = {
    val sourceType = process.root.ref.typ
    val sourceFactory = sourceFactories.getOrElse(sourceType, throw new IllegalArgumentException(s"Missing source factory of type: $sourceType"))
    val source = sourceFactory.create(process.metaData, process.root.ref.parameters.map(p => p.name -> p.value).toMap)
    val flinkSinks = allSinks(process)
    val defaultFlinkSink = SinkWithId("_", defaultSink.toFlinkSink)
    val allWithDefaultFlinkSinks = defaultFlinkSink :: flinkSinks.values.toList
    val splitResult = env
      .addSource[Any](source.toFlinkSource)(source.typeInformation)
      .flatMap(new InterpretationFunction(interpreterConfig, process, processTimeout))
      .split(new SplitFunction(flinkSinks, defaultFlinkSink))
    allWithDefaultFlinkSinks.foreach { sinkWithId =>
      splitResult
        .select(sinkWithId.id)
        .map { interpretationResult =>
          InputWithExectutionContext(interpretationResult.output, SynchronousExecutionContext.ctx)
        }
        .addSink(sinkWithId.flinkSink)
    }
  }

  private def allSinks(process: EspProcess): Map[SinkRef, SinkWithId] = {
    findAllSinkRefs(process.root).map { ref =>
      val sinkType = ref.typ
      val sinkFactory = sinkFactories.getOrElse(sinkType, throw new IllegalArgumentException(s"Missing sink factory of type: $sinkType"))
      val sink = sinkFactory.create(process.metaData, ref.parameters.map(p => p.name -> p.value).toMap)
      ref -> SinkWithId(sinkId(ref), sink.toFlinkSink)
    }.toMap
  }

  private def sinkId(ref: SinkRef) = { // TODO: what about duplicates?
    ref.parameters
      .map(_.value)
      .mkString(ref.typ, "_", "")
      .replaceAll("\\W", "_")
  }

  private def findAllSinkRefs(graph: Source): List[SinkRef] = {
    NodesCollector.collectNodes(graph).collect {
      case node.Sink(_, ref, _) => ref
    }
  }

}

object FlinkProcessRegistrar {

  private case class SinkWithId(id: String, flinkSink: SinkFunction[InputWithExectutionContext])

  class InterpretationFunction(config: => InterpreterConfig,
                               process: EspProcess,
                               processTimeout: Duration) extends FlatMapFunction[Any, InterpretationResult] {

    private lazy val interpreter = new Interpreter(config)

    override def flatMap(input: Any, collector: Collector[InterpretationResult]): Unit = {
      implicit val ec = SynchronousExecutionContext.ctx
      val resultFuture = interpreter.interpret(process, input).map { result =>
        collector.collect(result)
      }
      Await.result(resultFuture, processTimeout)
    }
  }

  class SplitFunction(flinkSinks: Map[SinkRef, SinkWithId],
                      defaultFlinkSink: SinkWithId) extends OutputSelector[InterpretationResult] {
    override def select(interpretationResult: InterpretationResult): Iterable[String] = {
      val id = interpretationResult.sinkRef match {
        case Some(ref) => flinkSinks(ref).id
        case None => defaultFlinkSink.id
      }
      Collections.singletonList(id)
    }
  }


}