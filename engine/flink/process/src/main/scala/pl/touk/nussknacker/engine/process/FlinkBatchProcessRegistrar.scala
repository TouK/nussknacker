package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import org.apache.flink.api.common.functions._
import org.apache.flink.api.java.RemoteEnvironment
import org.apache.flink.api.scala.{ExecutionEnvironment, _}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkBatchSink, FlinkBatchSource}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkBatchProcessRegistrar._
import pl.touk.nussknacker.engine.process.compiler.{CompiledProcessWithDeps, FlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, UserClassLoader}
import pl.touk.nussknacker.engine.splittedgraph.end.End

import scala.language.implicitConversions

class FlinkBatchProcessRegistrar(compileProcess: (EspProcess, ProcessVersion) => ClassLoader => CompiledProcessWithDeps,
                                 enableObjectReuse: Boolean) extends FlinkProcessRegistrar[ExecutionEnvironment] {

  import FlinkProcessRegistrar._

  override protected def isRemoteEnv(env: ExecutionEnvironment): Boolean = env.getJavaEnv.isInstanceOf[RemoteEnvironment]

  def register(env: ExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, testRunId: Option[TestRunId] = None): Unit = {
    prepareExecutionConfig(env.getConfig, enableObjectReuse)
    usingRightClassloader(env) {
      register(env, compileProcess(process, processVersion), testRunId)
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
        .map(new RateMeterFunction[Any]("source", part.id))
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
}
