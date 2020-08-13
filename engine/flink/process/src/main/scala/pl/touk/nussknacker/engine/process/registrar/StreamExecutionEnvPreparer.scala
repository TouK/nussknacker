package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.config.Config
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.apache.flink.api.java.tuple
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.operators.StreamOperatorFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer}
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, StateConfiguration}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

/*
  This trait is meant to be the place to configure StreamExecutionEnvironment. Here (e.g. in DefaultStreamExecutionEnvPreparer)
  we can put stuff that uses non-core Flink API, so that if someone needs to use e.g. older Flink version, they can
  use implementation that is suitable for them
 */
trait StreamExecutionEnvPreparer {

  def preRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: CompiledProcessWithDeps): Unit

  def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: CompiledProcessWithDeps): Unit

}

object DefaultStreamExecutionEnvPreparer {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  // We cannot use LazyLogging trait here because class already has LazyLogging and scala ends with cycle during resolution...
  private lazy val logger: Logger = Logger(LoggerFactory.getLogger(classOf[FlinkProcessRegistrar].getName))

  def apply(config: Config, executionConfigPreparer: ExecutionConfigPreparer, useDiskState: Boolean): DefaultStreamExecutionEnvPreparer = {

    // TODO checkpointInterval is deprecated - remove it in future
    val checkpointInterval = config.getAs[FiniteDuration](path = "checkpointInterval")
    if (checkpointInterval.isDefined) {
      logger.warn("checkpointInterval config property is deprecated, use checkpointConfig.checkpointInterval instead")
    }

    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
      .orElse(checkpointInterval.map(CheckpointConfig(_)))
    val diskStateBackend = config.getAs[RocksDBStateBackendConfig]("rocksDB")
      .map(StateConfiguration.prepareRocksDBStateBackend).filter(_ => useDiskState)

    new DefaultStreamExecutionEnvPreparer(checkpointConfig, diskStateBackend, executionConfigPreparer)
  }

}

class DefaultStreamExecutionEnvPreparer(checkpointConfig: Option[CheckpointConfig],
                                       diskStateBackend: Option[StateBackend],
                                       executionConfigPreparer: ExecutionConfigPreparer) extends StreamExecutionEnvPreparer with LazyLogging {

  override def preRegistration(env: StreamExecutionEnvironment, processWithDeps: CompiledProcessWithDeps): Unit = {

    executionConfigPreparer.prepareExecutionConfig(env.getConfig)(processWithDeps.metaData, processWithDeps.jobData.processVersion)

    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](processWithDeps.metaData)
    env.setRestartStrategy(processWithDeps.exceptionHandler.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)

    diskStateBackend match {
      case Some(backend) if streamMetaData.splitStateToDisk.getOrElse(false) =>
        logger.debug("Using disk state backend")
        env.setStateBackend(backend)
      case _ => logger.debug("Using default state backend")
    }
  }

  override def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: CompiledProcessWithDeps): Unit = {
    initializeStateDescriptors(env)
  }


  protected def configureCheckpoints(env: StreamExecutionEnvironment, streamMetaData: StreamMetaData): Unit = {
    val processSpecificCheckpointIntervalDuration = streamMetaData.checkpointIntervalDuration
    val checkpointIntervalToSet = processSpecificCheckpointIntervalDuration.orElse(checkpointConfig.map(_.checkpointInterval)).map(_.toMillis)
    checkpointIntervalToSet.foreach { checkpointIntervalToSetInMillis =>
      env.enableCheckpointing(checkpointIntervalToSetInMillis)
      env.getCheckpointConfig.setMinPauseBetweenCheckpoints(checkpointConfig.flatMap(_.minPauseBetweenCheckpoints).map(_.toMillis).getOrElse(checkpointIntervalToSetInMillis / 2))
      env.getCheckpointConfig.setMaxConcurrentCheckpoints(checkpointConfig.flatMap(_.maxConcurrentCheckpoints).getOrElse(1))
      checkpointConfig.flatMap(_.tolerableCheckpointFailureNumber).foreach(env.getCheckpointConfig.setTolerableCheckpointFailureNumber)
    }
  }

  //TODO: check if it's still valid in Flink 1.9
  //When serializing process graph (StateDescriptor:233) KryoSerializer is initialized without env configuration
  //Maybe it's a bug in flink??
  protected def initializeStateDescriptors(env: StreamExecutionEnvironment): Unit = {
    val config = env.getConfig
    env.getStreamGraph("", clearTransformations = false).getAllOperatorFactory.asScala.toSet[tuple.Tuple2[Integer, StreamOperatorFactory[_]]].map(_.f1).collect {
      case window: WindowOperator[_, _, _, _, _] => window.getStateDescriptor.initializeSerializerUnlessSet(config)
    }
  }
}
