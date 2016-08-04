package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.std.list._
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.compile.ProcessCompiler.NodeId
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.FlinkProcessRegistrar.{AggregateKeyByFunction, InterpretationFunction, SplitFunction}
import pl.touk.esp.engine.process.util.SynchronousExecutionContext
import pl.touk.esp.engine.{Interpreter, InterpreterConfig}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

class FlinkProcessRegistrar(interpreterConfig: => InterpreterConfig,
                            sourceFactories: Map[String, SourceFactory[_]],
                            sinkFactories: Map[String, SinkFactory],
                            processTimeout: Duration,
                            compiler: => ProcessCompiler = ProcessCompiler.default) {


  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess): Unit = {
    validate(process)

    val source: Source[Any] = createSource(process)

    val start = env
      .addSource[Any](source.toFlinkSource)(source.typeInformation)
      //chyba nie ascending????
      .assignAscendingTimestamps(source.extractTime)

    val splittedProcess = SplittedProcess(process.metaData, process.root)

    prepareParts(start, splittedProcess, Interpreter.InputParamName)
  }

  def validate(process: EspProcess): Unit = {
    FlinkProcessRegistrar.validateOrFail(compiler.compile(process))
  }

  def createSource(process: EspProcess): Source[Any] = {
    val sourceType = process.root.ref.typ
    val sourceFactory = sourceFactories.getOrElse(sourceType, throw new scala.IllegalArgumentException(s"Missing source factory of type: $sourceType"))
    sourceFactory
      .create(process.metaData, process.root.ref.parameters.map(p => p.name -> p.value).toMap)
      .asInstanceOf[Source[Any]]
  }

  def prepareParts[T](start: DataStream[T], splittedProcess: SplittedProcess, inputVarName: String): Unit = {

    val splitResult = start
      .flatMap(new InterpretationFunction[T](interpreterConfig, inputVarName, splittedProcess, processTimeout, compiler))
      .split(SplitFunction)


    splittedProcess.nextParts.foreach(part => {
      val select = splitResult.select(part.id)
      part match {
        case a: SinkPart => prepareSink(splittedProcess.processMetadata, a, select)
        case a: AggregatePart => prepareAggregate(a, select)
      }
    })
  }

  def prepareAggregate[T](aggregatePart: AggregatePart, splitResult: DataStream[InterpretationResult]): Unit = {

    val afterFold = splitResult
      .keyBy(new AggregateKeyByFunction(aggregatePart, compiler))
      .timeWindow(aggregatePart.durationInMillis, aggregatePart.slideInMillis)
      //FIXME: co to w sumie powinno byc??
      .fold(List[Any]())((a, b) => b.finalContext[Any](aggregatePart.aggregatedVar) :: a)
      .map(_.asJava)

    prepareParts(afterFold, aggregatePart.next, aggregatePart.aggregatedVar)
  }

  def prepareSink[T](processMetadata: MetaData,
                     sinkPart: SinkPart, splitResult: DataStream[InterpretationResult]): DataStreamSink[InputWithExectutionContext] = {
    splitResult
      .map { interpretationResult =>
        InputWithExectutionContext(interpretationResult.output, SynchronousExecutionContext.ctx)
      }
      .addSink(prepareSink(processMetadata, sinkPart.sink))
  }

  private def prepareSink(processMetadata: MetaData, sinkRef: SinkRef): SinkFunction[InputWithExectutionContext] = {
    val sinkType: String = sinkRef.typ
    val sinkFactory = sinkFactories.getOrElse(sinkType, throw new IllegalArgumentException(s"Missing sink factory of type: $sinkType"))
    sinkFactory.create(processMetadata, sinkRef.parameters.map(p => p.name -> p.value).toMap).toFlinkSink
  }

}

object FlinkProcessRegistrar {

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.unwrap.mkString("Compilation errors: ", ", ", ""))
  }

  class InterpretationFunction[T](config: => InterpreterConfig,
                                  inputVarName: String,
                                  splitProcess: SplittedProcess,
                                  processTimeout: Duration, compiler: => ProcessCompiler) extends FlatMapFunction[T, InterpretationResult] {

    private lazy val interpreter = new Interpreter(config)
    private lazy val compiledNode = validateOrFail(compiler.compile(splitProcess.node))

    override def flatMap(input: T, collector: Collector[InterpretationResult]): Unit = {
      implicit val ec = SynchronousExecutionContext.ctx
      val resultFuture = interpreter.interpret(splitProcess.processMetadata, compiledNode, input, inputVarName).map { result =>
        collector.collect(result)
      }
      Await.result(resultFuture, processTimeout)
    }
  }

  class AggregateKeyByFunction(aggregate: AggregatePart, compiler: => ProcessCompiler) extends (InterpretationResult => String) with Serializable {
    lazy val compiledExpression = validateOrFail(compiler.compile(aggregate.keyExpression)(NodeId(aggregate.id)))
    override def apply(result: InterpretationResult) = compiledExpression.evaluate(result.finalContext).toString
  }


  object SplitFunction extends OutputSelector[InterpretationResult] {
    override def select(interpretationResult: InterpretationResult): Iterable[String] = {
      interpretationResult.reference.map(_.id).toList.asJava
    }
  }

}