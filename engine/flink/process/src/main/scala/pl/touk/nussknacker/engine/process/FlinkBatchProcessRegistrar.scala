package pl.touk.nussknacker.engine.process

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions._
import org.apache.flink.api.java.RemoteEnvironment
import org.apache.flink.api.scala.{ExecutionEnvironment, _}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkBatchSource, FlinkBatchSink}
import pl.touk.nussknacker.engine.flink.util.ContextInitializingFunction
import pl.touk.nussknacker.engine.flink.util.metrics.InstantRateMeterWithCount
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkBatchProcessRegistrar._
import pl.touk.nussknacker.engine.process.compiler.{CompiledProcessWithDeps, FlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, Serializers, UserClassLoader}
import pl.touk.nussknacker.engine.splittedgraph.end.End
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.metrics.RateMeter
import pl.touk.nussknacker.engine.util.{SynchronousExecutionContext, ThreadUtils}

import scala.concurrent.{Await, ExecutionContext}
import scala.language.implicitConversions
import scala.util.control.NonFatal

class FlinkBatchProcessRegistrar(compileProcess: (EspProcess, ProcessVersion) => ClassLoader => CompiledProcessWithDeps,
                                 enableObjectReuse: Boolean) extends LazyLogging {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: ExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, testRunId: Option[TestRunId] = None): Unit = {
    Serializers.registerSerializers(env.getConfig)
    if (enableObjectReuse) {
      env.getConfig.enableObjectReuse()
      logger.info("Object reuse enabled")
    }

    usingRightClassloader(env) {
      register(env, compileProcess(process, processVersion), testRunId)
    }
  }

  private def usingRightClassloader(env: ExecutionEnvironment)(action: => Unit): Unit = {
    if (!env.getJavaEnv.isInstanceOf[RemoteEnvironment]) {
      val flinkLoaderSimulation =  FlinkUserCodeClassLoaders.childFirst(Array.empty, Thread.currentThread().getContextClassLoader, Array.empty)
      ThreadUtils.withThisAsContextClassLoader[Unit](flinkLoaderSimulation)(action)
    } else {
      action
    }
  }

  private def register(env: ExecutionEnvironment, compiledProcessWithDeps: ClassLoader => CompiledProcessWithDeps,
                       testRunId: Option[TestRunId]): Unit = {
    val processWithDeps = compiledProcessWithDeps(UserClassLoader.get("root"))
    val metaData = processWithDeps.metaData

    val batchMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[BatchMetaData](metaData)
    env.setRestartStrategy(processWithDeps.exceptionHandler.restartStrategy)
    batchMetaData.parallelism.foreach(env.setParallelism)

    // TODO: multiple sources
    registerSourcePart(processWithDeps.sources.head.asInstanceOf[SourcePart])

    def registerSourcePart(part: SourcePart): Unit = {
      val source = part.obj.asInstanceOf[FlinkBatchSource[Any]]

      val start = env
        .createInput[Any](source.toFlink)(source.classTag, source.typeInformation)
        .name(s"${metaData.id}-source")
        .map(new RateMeterFunction[Any]("source"))
        .map(InitContextFunction(metaData.id, part.node.id))
        .flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, part.node, part.validationContext))
        .name(s"${metaData.id}-${part.node.id}-interpretation")
        .map(new TagInterpretationResultFunction)

      registerParts(start, part.nextParts, part.ends)
    }

    def registerParts(start: DataSet[TaggedInterpretationResult],
                      nextParts: Seq[SubsequentPart],
                      ends: Seq[End]): Unit = {
      // TODO: endmeter sink
      nextParts.foreach { nextPart =>
        val subsequentStart = start.filter(_.tagName == nextPart.id).map(_.interpretationResult)
        registerSubsequentPart(subsequentStart, nextPart)
      }
    }

    def registerSubsequentPart(start: DataSet[InterpretationResult],
                               processPart: SubsequentPart): Unit = {
      processPart match {
        case part@SinkPart(sink: FlinkBatchSink, _, validationContext) =>
          val startAfterSinkEvaluated = start
            .map(_.finalContext)
            .flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, part.node, validationContext))
            .name(s"${metaData.id}-${part.node.id}-function")

          val withSinkAdded =
            testRunId match {
              case None =>
                startAfterSinkEvaluated
                  .map(_.output)
                  .output(sink.toFlink)
              case Some(_) =>
                // TODO: test run
                throw new NotImplementedError("Test run is not implemented")
            }

          withSinkAdded.name(s"${metaData.id}-${part.id}-sink")
        // TODO: custom node support
        case part =>
          throw new NotImplementedError(s"${part.getClass.getSimpleName} is not implemented")
      }
    }
  }
}


object FlinkBatchProcessRegistrar {

  import net.ceedubs.ficus.Ficus._

  private final val EndId = "$end"

  def apply(compiler: FlinkProcessCompiler, config: Config): FlinkBatchProcessRegistrar = {

    val enableObjectReuse = config.getOrElse[Boolean]("enableObjectReuse", true)

    new FlinkBatchProcessRegistrar(
      compileProcess = compiler.compileProcess,
      enableObjectReuse = enableObjectReuse
    )
  }

  case class TaggedInterpretationResult(tagName: String, interpretationResult: InterpretationResult)

  // TODO: extract common functions

  class SyncInterpretationFunction(val compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps,
                                   node: SplittedNode[_], validationContext: ValidationContext)
    extends RichFlatMapFunction[Context, InterpretationResult] with WithCompiledProcessDeps {

    private lazy implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)
    import compiledProcessWithDeps._

    override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
      (try {
        Await.result(interpreter.interpret(compiledNode, metaData, input), processTimeout)
      } catch {
        case NonFatal(error) => Right(EspExceptionInfo(None, error, input))
      }) match {
        case Left(ir) =>
          exceptionHandler.handling(None, input)(ir.foreach(collector.collect))
        case Right(info) =>
          exceptionHandler.handle(info)
      }
    }
  }

  class TagInterpretationResultFunction extends MapFunction[InterpretationResult, TaggedInterpretationResult] {
    override def map(interpretationResult: InterpretationResult): TaggedInterpretationResult = {
      val tagName = interpretationResult.reference match {
        case NextPartReference(id) => id
        case JoinReference(id, _) => id
        case _: EndingReference => EndId
      }
      TaggedInterpretationResult(tagName, interpretationResult)
    }
  }

  class RateMeterFunction[T](groupId: String) extends RichMapFunction[T, T] {
    private var instantRateMeter : RateMeter = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      instantRateMeter = InstantRateMeterWithCount.register(getRuntimeContext.getMetricGroup.addGroup(groupId))
    }

    override def map(value: T): T = {
      instantRateMeter.mark()
      value
    }
  }

  case class InitContextFunction(processId: String, taskName: String) extends RichMapFunction[Any, Context] with ContextInitializingFunction {

    override def open(parameters: Configuration): Unit = {
      init(getRuntimeContext)
    }

    override def map(input: Any): Context = newContext.withVariable(Interpreter.InputParamName, input)
  }
}
