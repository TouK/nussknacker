package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.std.list._
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.process.{InputWithExectutionContext, SinkFactory, Source, SourceFactory}
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.FlinkProcessRegistrar._
import pl.touk.esp.engine.process.util.SynchronousExecutionContext
import pl.touk.esp.engine.split.ProcessSplitter
import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode.{NextNode, PartRef, SplittedNode}
import pl.touk.esp.engine.splittedgraph.{SplittedProcess, splittednode}
import pl.touk.esp.engine.{Interpreter, InterpreterConfig}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

class FlinkProcessRegistrar(interpreterConfig: => InterpreterConfig,
                            sourceFactories: => Map[String, SourceFactory[_]],
                            sinkFactories: => Map[String, SinkFactory],
                            processTimeout: => Duration,
                            compiler: => ProcessCompiler = ProcessCompiler.default) {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess): Unit = {
    val splittedProcess = ProcessSplitter.split(process)
    validateOrFail(compiler.validate(splittedProcess))
    register(env, splittedProcess)
  }

  private def register(env: StreamExecutionEnvironment, process: SplittedProcess): Unit = {
    registerSourcePart(process.source)

    def registerSourcePart(part: SourcePart): Unit = {
      val source = createSource(part)
      val timeExtractionFunction = source.timeExtractionFunction

      timeExtractionFunction.foreach(_ => env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime))

      val newStart = env
        .addSource[Any](source.toFlinkSource)(source.typeInformation)
        //chyba nie ascending????
      val withAssigned = timeExtractionFunction.map(newStart.assignAscendingTimestamps).getOrElse(newStart)
        .flatMap(new InitialInterpretationFunction(compiler, part.source, interpreterConfig, process.metaData, Interpreter.InputParamName, processTimeout))
        .split(SplitFunction)
      registerParts(withAssigned, part.nextParts)
    }

    def registerSubsequentPart[T](start: DataStream[T],
                                  processPart: SubsequentPart): Unit =
      processPart match {
        case part: AggregateExpressionPart =>
          val newStart = start.asInstanceOf[DataStream[InterpretationResult]]
            .keyBy(new AggregateKeyByFunction(compiler, part.aggregate, interpreterConfig, processTimeout))
            .timeWindow(part.durationInMillis, part.slideInMillis)
            .fold(List[Any]())((a, b) => b.finalContext[Any](part.aggregatedVar) :: a)
            .map(_.asJava)
          registerSubsequentPart(newStart, part.nextPart)
        case part: AfterAggregationPart =>
          val typedStart = start.asInstanceOf[DataStream[Any]] // List[Any]
          part.next match {
            case NextNode(node) =>
              val newStart = typedStart
                .flatMap(new InitialInterpretationFunction(compiler, node, interpreterConfig, process.metaData, part.aggregatedVar, processTimeout))
                .split(SplitFunction)
              registerParts(newStart, part.nextParts)
            case PartRef(id) =>
              assert(part.nextParts.size == 1, "Aggregate ended up with part ref should have one next part")
              assert(part.nextParts.head.id == id, "Aggregate ended up with part ref should have one next part with the same id as in ref")
              registerSubsequentPart(typedStart, part.nextParts.head)
          }
        case part: SinkPart =>
          start.asInstanceOf[DataStream[InterpretationResult]]
            .flatMap(new IntermediateInterpretationFunction(compiler, part.sink, interpreterConfig, processTimeout))
            .map { (interpretationResult: InterpretationResult) =>
              InputWithExectutionContext(interpretationResult.output, SynchronousExecutionContext.ctx)
            }
            .addSink(createSink(part))
      }

    def registerParts(start: SplitStream[InterpretationResult],
                      nextParts: Seq[SubsequentPart]) = {
      nextParts.foreach { part =>
        registerSubsequentPart(start.select(part.id), part)
      }
      // TODO: register default sink
    }

    def createSource(part: SourcePart): Source[Any] = {
      val sourceType = part.ref.typ
      val sourceFactory = sourceFactories.getOrElse(sourceType, throw new scala.IllegalArgumentException(s"Missing source factory of type: $sourceType"))
      sourceFactory
        .create(process.metaData, part.ref.parameters.map(p => p.name -> p.value).toMap)
        .asInstanceOf[Source[Any]]
    }

    def createSink(part: SinkPart): SinkFunction[InputWithExectutionContext] = {
      val sinkType = part.ref.typ
      val sinkFactory = sinkFactories.getOrElse(sinkType, throw new IllegalArgumentException(s"Missing sink factory of type: $sinkType"))
      sinkFactory
        .create(process.metaData, part.ref.parameters.map(p => p.name -> p.value).toMap)
        .toFlinkSink
    }
  }

}

object FlinkProcessRegistrar {

  private final val DefaultSinkId = "$"

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.unwrap.mkString("Compilation errors: ", ", ", ""))
  }

  class InitialInterpretationFunction(compiler: => ProcessCompiler,
                                      node: SplittedNode,
                                      config: => InterpreterConfig,
                                      metaData: MetaData,
                                      inputParamName: String,
                                      processTimeout: Duration) extends FlatMapFunction[Any, InterpretationResult] {

    private lazy val interpreter = new Interpreter(config)
    private lazy val compiledNode = validateOrFail(compiler.compile(node))

    override def flatMap(input: Any, collector: Collector[InterpretationResult]): Unit = {
      implicit val ec = SynchronousExecutionContext.ctx
      val resultFuture = interpreter.interpret(compiledNode, metaData, input, inputParamName).map { result =>
        collector.collect(result)
      }
      Await.result(resultFuture, processTimeout)
    }
  }

  class IntermediateInterpretationFunction(compiler: => ProcessCompiler,
                                           node: SplittedNode,
                                           config: => InterpreterConfig,
                                           processTimeout: Duration) extends FlatMapFunction[InterpretationResult, InterpretationResult] {

    private lazy val interpreter = new Interpreter(config)
    private lazy val compiledNode = validateOrFail(compiler.compile(node))

    override def flatMap(input: InterpretationResult, collector: Collector[InterpretationResult]): Unit = {
      implicit val ec = SynchronousExecutionContext.ctx
      val resultFuture = interpreter.interpret(compiledNode, input.finalContext).map { result =>
        collector.collect(result)
      }
      Await.result(resultFuture, processTimeout)
    }
  }

  object SplitFunction extends OutputSelector[InterpretationResult] {
    override def select(interpretationResult: InterpretationResult): Iterable[String] = {
      interpretationResult.reference match {
        case NextPartReference(id) => List(id).asJava
        case DefaultSinkReference => List(DefaultSinkId).asJava // TODO: default sink won't be registered
        case EndReference => throw new IllegalStateException("Non-sink interpretation shouldn't ended up by end reference")
      }
    }
  }

  class AggregateKeyByFunction(compiler: => ProcessCompiler,
                               node: splittednode.Aggregate,
                               config: => InterpreterConfig,
                               processTimeout: Duration) extends (InterpretationResult => String) with Serializable {

    private lazy val interpreter = new Interpreter(config)
    private lazy val compiledNode = validateOrFail(compiler.compile(node))

    override def apply(result: InterpretationResult) = {
      implicit val ec = SynchronousExecutionContext.ctx
      val resultFuture = interpreter.interpret(compiledNode, result.finalContext).map(_.output.toString)
      Await.result(resultFuture, processTimeout)
    }

  }

}