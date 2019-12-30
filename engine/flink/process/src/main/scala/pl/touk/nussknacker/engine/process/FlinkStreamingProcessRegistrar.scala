package pl.touk.nussknacker.engine.process

import java.util.Collections
import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.tuple
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.Gauge
import org.apache.flink.runtime.state.{AbstractStateBackend, StateBackend}
import org.apache.flink.streaming.api.environment.RemoteStreamEnvironment
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, ProcessFunction}
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, ChainingStrategy, OneInputStreamOperator, StreamOperator}
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.{TimeCharacteristic, datastream}
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.SinkInvocationCollector
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, _}
import pl.touk.nussknacker.engine.flink.util.metrics.InstantRateMeterWithCount
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar._
import pl.touk.nussknacker.engine.process.compiler.{CompiledProcessWithDeps, FlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, StateConfiguration, UserClassLoader}
import pl.touk.nussknacker.engine.splittedgraph.end.{BranchEnd, DeadEnd, End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.metrics.RateMeter
import shapeless.syntax.typeable._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class FlinkStreamingProcessRegistrar(compileProcess: (EspProcess, ProcessVersion) => (ClassLoader) => CompiledProcessWithDeps,
                                     eventTimeMetricDuration: FiniteDuration,
                                     checkpointInterval: FiniteDuration,
                                     enableObjectReuse: Boolean, diskStateBackend: Option[StateBackend])
  extends FlinkProcessRegistrar[StreamExecutionEnvironment] {

  import FlinkProcessRegistrar._

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  override protected def isRemoteEnv(env: StreamExecutionEnvironment): Boolean = env.getJavaEnv.isInstanceOf[RemoteStreamEnvironment]

  def register(env: StreamExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, testRunId: Option[TestRunId] = None): Unit = {
    prepareExecutionConfig(env.getConfig, enableObjectReuse)
    usingRightClassloader(env) {
      register(env, compileProcess(process, processVersion), testRunId)
    }
    initializeStateDescriptors(env)
  }

  //When serializing process graph (StateDescriptor:233) KryoSerializer is initialized without env configuration
  //Maybe it's a bug in flink??
  //TODO: is it the only place where we should do it??
  private def initializeStateDescriptors(env: StreamExecutionEnvironment): Unit = {
    val config = env.getConfig
    env.getStreamGraph.getOperators.asScala.toSet[tuple.Tuple2[Integer, StreamOperator[_]]].map(_.f1).collect {
      case window:WindowOperator[_, _, _, _, _] => window.getStateDescriptor.initializeSerializerUnlessSet(config)
    }
  }

  private def register(env: StreamExecutionEnvironment, compiledProcessWithDeps: ClassLoader => CompiledProcessWithDeps,
                       testRunId: Option[TestRunId]): Unit = {


    //here we are sure the classloader is ok
    val processWithDeps = compiledProcessWithDeps(UserClassLoader.get("root"))
    val metaData = processWithDeps.metaData

    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](metaData)
    env.setRestartStrategy(processWithDeps.exceptionHandler.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)

    val asyncExecutionContextPreparer = processWithDeps.asyncExecutionContextPreparer

    diskStateBackend match {
      case Some(backend) if streamMetaData.splitStateToDisk.getOrElse(false) =>
        logger.debug("Using disk state backend")
        env.setStateBackend(backend)
      case _ => logger.debug("Using default state backend")
    }

    {
      val branchEnds = processWithDeps.sources.toList
        .collect { case e: SourcePart => e }
        .map(registerSourcePart).foldLeft(Map[BranchEndDefinition, DataStream[InterpretationResult]]()){ _ ++ _}

      //TODO JOIN - here we need recursion for nested joins
      branchEnds.groupBy(_._1.joinId).foreach {
        case (joinId, inputs) =>
          val joinPart = processWithDeps.sources.toList
            .collect { case e: CustomNodePart if e.id == joinId => e } match {
            case head::Nil => head
            case Nil => throw new IllegalArgumentException(s"Invalid process structure, no $joinId defined")
            case moreThanOne => throw new IllegalArgumentException(s"Invalid process structure, more than one $joinId defined: $moreThanOne")
          }

          val transformer = joinPart.transformer match {
            case joinTransformer: FlinkCustomJoinTransformation => joinTransformer
            case JoinContextTransformation(_, impl: FlinkCustomJoinTransformation) => impl
            case other =>
              throw new IllegalArgumentException(s"Unknown join node transformer: $other")
          }
          val customNodeContext = FlinkCustomNodeContext(metaData,
            joinId, processWithDeps.processTimeout,
            new FlinkLazyParameterFunctionHelper(createInterpreter(compiledProcessWithDeps)),
            processWithDeps.signalSenders)


          val outputVar = joinPart.node.data.outputVar.get
          val newContextFun = (ir: ValueWithContext[_]) => ir.context.withVariable(outputVar, ir.value)

          val newStart = transformer.transform(inputs
              .map(kv => (kv._1.id, kv._2.map(_.finalContext))), customNodeContext).map(newContextFun)

          val afterSplit = wrapAsync(newStart, joinPart.node, joinPart.validationContext, "branchInterpretation")

          registerParts(afterSplit, joinPart.nextParts, joinPart.ends)
      }
    }

    def registerSourcePart(part: SourcePart): Map[BranchEndDefinition, DataStream[InterpretationResult]] = {
      //TODO: get rid of cast (but how??)
      val source = part.obj.asInstanceOf[FlinkSource[Any]]

      val timestampAssigner = source.timestampAssigner

      env.setStreamTimeCharacteristic(if (timestampAssigner.isDefined) TimeCharacteristic.EventTime else TimeCharacteristic.IngestionTime)

      val newStart = env
        .addSource[Any](source.toFlinkSource)(source.typeInformation)
        .name(s"${metaData.id}-source")
      val withAssigned = timestampAssigner.map {
        case periodic: AssignerWithPeriodicWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(periodic)
        case punctuated: AssignerWithPunctuatedWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(punctuated)
      }.map(_.transform("even-time-meter", new EventTimeDelayMeterFunction("eventtimedelay", eventTimeMetricDuration)))
        .getOrElse(newStart)
        .map(new RateMeterFunction[Any]("source"))
          .map(InitContextFunction(metaData.id, part.node.id))

      val asyncAssigned = wrapAsync(withAssigned, part.node, part.validationContext, "interpretation")

      val branchEnds = part.ends.flatMap(_.cast[BranchEnd]).map(be =>  be.definition ->
        asyncAssigned.getSideOutput(OutputTag[InterpretationResult](be.nodeId))).toMap

      registerParts(asyncAssigned, part.nextParts, part.ends) ++ branchEnds
    }

    def registerParts(start: DataStream[Unit],
                      nextParts: Seq[SubsequentPart],
                      ends: Seq[End]) : Map[BranchEndDefinition, DataStream[InterpretationResult]] = {

      start.getSideOutput(OutputTag[InterpretationResult](EndId))
        .addSink(new EndRateMeterFunction(ends))
      nextParts.map { part =>
        registerSubsequentPart(start.getSideOutput(OutputTag[InterpretationResult](part.id)), part)
      }.foldLeft(Map[BranchEndDefinition, DataStream[InterpretationResult]]()){_ ++ _}
    }

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Map[BranchEndDefinition, DataStream[InterpretationResult]] =
      processPart match {

        case part@SinkPart(sink: FlinkSink, sinkDef, validationContext) => {
          val startAfterSinkEvaluated = wrapAsync(start.map(_.finalContext), part.node, validationContext, "function")
            .getSideOutput(OutputTag[InterpretationResult](EndId))
            .map(new EndRateMeterFunction(part.ends))

          val withSinkAdded =
            //TODO: maybe this logic should be moved to compiler instead?
            testRunId match {
              case None =>
                startAfterSinkEvaluated
                  .map(_.output)
                  .addSink(sink.toFlinkFunction)
              case Some(runId) =>
                val typ = part.node.data.ref.typ
                val prepareFunction = sink.testDataOutput.getOrElse(throw new IllegalArgumentException(s"Sink $typ cannot be mocked"))
                val collectingSink = SinkInvocationCollector(runId, part.id, typ, prepareFunction)
                startAfterSinkEvaluated.addSink(new CollectingSinkFunction(compiledProcessWithDeps, collectingSink, part))
            }

          withSinkAdded.name(s"${metaData.id}-${part.id}-sink")
          Map()
        }

        case part:SinkPart =>
          throw new IllegalArgumentException(s"Process can only use flink sinks, instead given: ${part.obj}")
        case CustomNodePart(transformerObj, node, validationContext, nextParts, ends) =>

          val transformer = transformerObj match {
            case t: FlinkCustomStreamTransformation => t
            case ContextTransformation(_, impl: FlinkCustomStreamTransformation) => impl
            case other =>
              throw new IllegalArgumentException(s"Unknown custom node transformer: $other")
          }

          val newContextFun = (ir: ValueWithContext[_]) => node.data.outputVar match {
            case Some(name) => ir.context.withVariable(name, ir.value)
            case None => ir.context
          }

          val customNodeContext = FlinkCustomNodeContext(metaData,
            node.id, processWithDeps.processTimeout,
            new FlinkLazyParameterFunctionHelper(createInterpreter(compiledProcessWithDeps)),
            processWithDeps.signalSenders)
          val newStart = transformer.transform(start.map(_.finalContext), customNodeContext)
              .map(newContextFun)
          val afterSplit = wrapAsync(newStart, node, validationContext, "customNodeInterpretation")

          registerParts(afterSplit, nextParts, ends)
      }

    def wrapAsync(beforeAsync: DataStream[Context], node: SplittedNode[_], validationContext: ValidationContext, name: String) : DataStream[Unit] = {
      (if (streamMetaData.shouldUseAsyncInterpretation) {
        val asyncFunction = new AsyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, asyncExecutionContextPreparer)
        new DataStream(datastream.AsyncDataStream.orderedWait(beforeAsync.javaStream, asyncFunction,
          processWithDeps.processTimeout.toMillis, TimeUnit.MILLISECONDS, asyncExecutionContextPreparer.bufferSize))
      } else {
        beforeAsync.flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, node, validationContext))
      }).name(s"${metaData.id}-${node.id}-$name").process(new SplitFunction)
    }
  }

  private def configureCheckpoints(env: StreamExecutionEnvironment, streamMetaData: StreamMetaData): Unit = {
    val checkpointIntervalToSetInMillis = streamMetaData.checkpointIntervalDuration.getOrElse(checkpointInterval).toMillis
    env.enableCheckpointing(checkpointIntervalToSetInMillis)

    //TODO: should this be configurable?
    env.getCheckpointConfig.setMinPauseBetweenCheckpoints(checkpointIntervalToSetInMillis / 2)
    env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
  }
}


object FlinkStreamingProcessRegistrar {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  private final val EndId = "$end"

  def apply(compiler: FlinkProcessCompiler, config: Config) : FlinkStreamingProcessRegistrar = {

    val enableObjectReuse = config.getOrElse[Boolean]("enableObjectReuse", true)
    val eventTimeMetricDuration = config.getOrElse[FiniteDuration]("eventTimeMetricSlideDuration", 10.seconds)
    val checkpointInterval = config.as[FiniteDuration]("checkpointInterval")

    new FlinkStreamingProcessRegistrar(
      compileProcess = compiler.compileProcess,
      eventTimeMetricDuration = eventTimeMetricDuration,
      checkpointInterval = checkpointInterval,
      enableObjectReuse = enableObjectReuse,
      diskStateBackend =  {
        if (compiler.diskStateBackendSupport) {
          config.getAs[RocksDBStateBackendConfig]("rocksDB").map(StateConfiguration.prepareRocksDBStateBackend)
        } else None
      }
    )
  }

  private def createInterpreter(compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps) =
                               (runtimeContext: RuntimeContext) =>
    new FlinkCompilerLazyInterpreterCreator(runtimeContext, compiledProcessWithDepsProvider(runtimeContext.getUserCodeClassLoader))

  class AsyncInterpretationFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                                    node: SplittedNode[_], validationContext: ValidationContext, asyncExecutionContextPreparer: AsyncExecutionContextPreparer)
    extends RichAsyncFunction[Context, InterpretationResult] with LazyLogging with WithCompiledProcessDeps {


    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)
    import compiledProcessWithDeps._

    private var executionContext : ExecutionContext = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      executionContext = asyncExecutionContextPreparer.prepareExecutionContext(compiledProcessWithDeps.metaData.id,
        getRuntimeContext.getExecutionConfig.getParallelism)
    }


    private def handleException(collector: ResultFuture[InterpretationResult], info: EspExceptionInfo[_<:Throwable]): Unit = {
      try {
        exceptionHandler.handle(info)
        collector.complete(Collections.emptyList[InterpretationResult]())
      } catch {
        case NonFatal(e) => logger.warn("Unexpected fail, refusing to collect??", e); collector.completeExceptionally(e)
      }
    }

    override def asyncInvoke(input: Context, collector: ResultFuture[InterpretationResult]) : Unit = {
      implicit val ec = executionContext
      try {
        interpreter.interpret(compiledNode, metaData, input)
          .onComplete {
            case Success(Left(result)) => collector.complete(result.asJava)
            case Success(Right(exInfo)) => handleException(collector, exInfo)
            case Failure(ex) =>
              logger.warn("Unexpected error", ex)
              handleException(collector, EspExceptionInfo(None, ex, input))
          }
      } catch {
        case NonFatal(ex) =>
          logger.warn("Unexpected error", ex)
          handleException(collector, EspExceptionInfo(None, ex, input))
      }
    }

    override def close(): Unit = {
      super.close()
      asyncExecutionContextPreparer.close()
    }

  }

  class CollectingSinkFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                               collectingSink: SinkInvocationCollector, sink: SinkPart)
    extends RichSinkFunction[InterpretationResult] with WithCompiledProcessDeps {

    override def invoke(value: InterpretationResult) = {
      compiledProcessWithDeps.exceptionHandler.handling(Some(sink.id), value.finalContext) {
        collectingSink.collect(value)
      }
    }
  }

  class EventTimeDelayMeterFunction[T](groupId: String, slidingWindow: FiniteDuration)
    extends AbstractStreamOperator[T] with OneInputStreamOperator[T, T] {

    setChainingStrategy(ChainingStrategy.ALWAYS)

    lazy val histogramMeter = new DropwizardHistogramWrapper(
      new Histogram(
        new SlidingTimeWindowReservoir(slidingWindow.toMillis, TimeUnit.MILLISECONDS)))

    lazy val minimalDelayGauge = new Gauge[Long] {
      override def getValue = {
        val now = System.currentTimeMillis()
        now - lastElement.getOrElse(now)
      }
    }

    var lastElement : Option[Long] = None

    override def open(): Unit = {
      super.open()

      val group = getRuntimeContext.getMetricGroup.addGroup(groupId)
      group.histogram("histogram", histogramMeter)
      group.gauge[Long, Gauge[Long]]("minimalDelay", minimalDelayGauge)
    }

    override def processElement(element: StreamRecord[T]): Unit = {
      if (element.hasTimestamp) {
        val timestamp = element.getTimestamp
        val delay = System.currentTimeMillis() - timestamp
        histogramMeter.update(delay)
        lastElement = Some(lastElement.fold(timestamp)(math.max(_, timestamp)))
      }
      output.collect(element)
    }

    override def processWatermark(mark: Watermark): Unit = {
      output.emitWatermark(mark)
    }

  }

  class EndRateMeterFunction(ends: Seq[End]) extends AbstractRichFunction
    with MapFunction[InterpretationResult, InterpretationResult] with SinkFunction[InterpretationResult] {

    @transient private var meterByReference: Map[PartReference, RateMeter] = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      val parentGroupForNormalEnds = getRuntimeContext.getMetricGroup.addGroup("end")
      val parentGroupForDeadEnds = getRuntimeContext.getMetricGroup.addGroup("dead_end")

      def registerRateMeter(end: End) = {
        val baseGroup = end match {
          case normal: NormalEnd => parentGroupForNormalEnds
          case dead: DeadEnd => parentGroupForDeadEnds
          case e: BranchEnd => parentGroupForDeadEnds
        }
        InstantRateMeterWithCount.register(baseGroup.addGroup(end.nodeId))
      }

      meterByReference = ends.map { end =>
        val reference = end match {
          case NormalEnd(nodeId) =>  EndReference(nodeId)
          case DeadEnd(nodeId) =>  DeadEndReference(nodeId)
          case BranchEnd(definition) => definition.joinReference
        }
        reference -> registerRateMeter(end)
      }.toMap[PartReference, RateMeter]
    }

    override def map(value: InterpretationResult) = {
      val meter = meterByReference.getOrElse(value.reference, throw new IllegalArgumentException("Unexpected reference: " + value.reference))
      meter.mark()
      value
    }

    override def invoke(value: InterpretationResult) = {
      map(value)
    }
  }

  class SplitFunction extends ProcessFunction[InterpretationResult, Unit] {

    //we eagerly create TypeInformation here, creating it during OutputTag construction would be too expensive
    private lazy val typeInfo: TypeInformation[InterpretationResult] = implicitly[TypeInformation[InterpretationResult]]

    override def processElement(interpretationResult: InterpretationResult, ctx: ProcessFunction[InterpretationResult, Unit]#Context,
                                out: Collector[Unit]): Unit = {
      val tagName = interpretationResult.reference match {
        case NextPartReference(id) => id
        //TODO JOIN - this is a bit weird, probably refactoring of splitted process structures will help...
        case JoinReference(id, _) => id
        case _: EndingReference => EndId
      }
      ctx.output(OutputTag[InterpretationResult](tagName)(typeInfo), interpretationResult)
    }
  }

}

class FlinkCompilerLazyInterpreterCreator(runtimeContext: RuntimeContext, withDeps: CompiledProcessWithDeps)
  extends CompilerLazyParameterInterpreter {

  //TODO: is this good place?
  withDeps.open(runtimeContext)

  val deps: LazyInterpreterDependencies = withDeps.lazyInterpreterDeps

  val metaData: MetaData = withDeps.metaData

  override def close(): Unit = {
    withDeps.close()
  }
}