package pl.touk.nussknacker.engine.process.registrar

import java.util.function.Consumer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.java.tuple
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.operators.StreamOperatorFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, StateConfiguration}
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer}

import scala.collection.JavaConverters._

/*
  This trait is meant to be the place to configure StreamExecutionEnvironment. Here (e.g. in DefaultStreamExecutionEnvPreparer)
  we can put stuff that uses non-core Flink API, so that if someone needs to use e.g. older Flink version, they can
  use implementation that is suitable for them
 */
trait StreamExecutionEnvPreparer {

  def preRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData): Unit

  def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData): Unit

  def flinkClassLoaderSimulation: ClassLoader
}

class DefaultStreamExecutionEnvPreparer(checkpointConfig: Option[CheckpointConfig],
                                        rocksDBStateBackendConfig: Option[RocksDBStateBackendConfig],
                                       executionConfigPreparer: ExecutionConfigPreparer) extends StreamExecutionEnvPreparer with LazyLogging {

  override def preRegistration(env: StreamExecutionEnvironment, processWithDeps: FlinkProcessCompilerData): Unit = {

    executionConfigPreparer.prepareExecutionConfig(env.getConfig)(processWithDeps.metaData, processWithDeps.jobData.processVersion)

    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](processWithDeps.metaData)
    env.setRestartStrategy(processWithDeps.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)

    rocksDBStateBackendConfig match {
      case Some(config) if streamMetaData.splitStateToDisk.getOrElse(false) =>
        logger.debug("Using disk state backend")
        configureRocksDBBackend(env, config)
      case _ => logger.debug("Using default state backend")
    }
  }

  protected def configureRocksDBBackend(env: StreamExecutionEnvironment, config: RocksDBStateBackendConfig): Unit = {
    env.setStateBackend(StateConfiguration.prepareRocksDBStateBackend(config).asInstanceOf[StateBackend])
  }

  override def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData): Unit = {
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

  override def flinkClassLoaderSimulation: ClassLoader = {
    FlinkUserCodeClassLoaders.childFirst(Array.empty,
      Thread.currentThread().getContextClassLoader, Array.empty, new Consumer[Throwable] {
        override def accept(t: Throwable): Unit = throw t
      }
    )
  }
}
