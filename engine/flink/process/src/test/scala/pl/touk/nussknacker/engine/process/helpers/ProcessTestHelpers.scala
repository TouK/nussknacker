package pl.touk.nussknacker.engine.process.helpers

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.{ExecutionConfig, JobExecutionResult}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.process.{FlinkStreamingProcessRegistrar, SimpleJavaEnum}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.testing.LocalModelData

object ProcessTestHelpers {

  object processInvoker {

    def invokeWithSampleData(process: EspProcess, data: List[SimpleRecord],
                             config: Configuration = new Configuration(),
                             processVersion: ProcessVersion = ProcessVersion.empty,
                             parallelism: Int = 1): JobExecutionResult = {

      val creator: ProcessConfigCreator = prepareCreator(data, KafkaConfig("http://notexist.pl", None, None))
      invoke(process, creator, config, processVersion, parallelism)
    }

    def invoke(process: EspProcess, creator: ProcessConfigCreator,
               configuration: Configuration = new Configuration(),
               processVersion: ProcessVersion = ProcessVersion.empty,
               parallelism: Int = 1): JobExecutionResult = {
      FlinkTestConfiguration.addQueryableStatePortRanges(configuration)
      val env = StreamExecutionEnvironment.createLocalEnvironment(parallelism, configuration)
      env.getConfig.disableSysoutLogging
      val config = ConfigFactory.load()
      FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config).register(env, process, processVersion)

      MockService.clear()
      env.execute(process.id)
    }

    def prepareCreator(data: List[SimpleRecord], kafkaConfig: KafkaConfig): ProcessConfigCreator = new ProcessConfigCreator {

      override def services(config: Config): Map[String, WithCategories[Service with TimeMeasuringService]] = Map(
        "logService" -> WithCategories(new MockService),
        "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService)
      )

      override def sourceFactories(config: Config): Map[String, WithCategories[FlinkSourceFactory[_]]] = Map(
        "input" -> WithCategories(SampleNodes.simpleRecordSource(data)),
        "intInputWithParam" -> WithCategories(new IntParamSourceFactory(new ExecutionConfig)),
        "kafka-keyvalue" -> WithCategories(new KeyValueKafkaSourceFactory(kafkaConfig))
      )

      override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
        "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink)),
        "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts)),
        "sinkForStrings" -> WithCategories(SinkFactory.noParam(SinkForStrings))
      )

      override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
        "stateCustom" -> WithCategories(StateCustomNode),
        "customFilter" -> WithCategories(CustomFilter),
        "customFilterContextTransformation" -> WithCategories(CustomFilterContextTransformation),
        "customContextClear" -> WithCategories(CustomContextClear),
        "sampleJoin" -> WithCategories(CustomJoin),
        "joinBranchExpression" -> WithCategories(CustomJoinUsingBranchExpressions)
      )

      override def listeners(config: Config) = List()

      override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
        ExceptionHandlerFactory.noParams(_ => RecordingExceptionHandler)


      override def expressionConfig(config: Config): ExpressionConfig = {
        val dictId = EmbeddedDictDefinition.enumDictId(classOf[SimpleJavaEnum])
        val dictDef = EmbeddedDictDefinition.forJavaEnum(classOf[SimpleJavaEnum])
        val globalProcessVariables = Map(
          "processHelper" -> WithCategories(ProcessHelper),
          "enum" -> WithCategories(DictInstance(dictId, dictDef)))
        ExpressionConfig(globalProcessVariables, List.empty, dictionaries = Map(dictId -> WithCategories(dictDef)))
      }

      override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map()

      override def buildInfo(): Map[String, String] = Map.empty
    }

    def invokeWithKafka(process: EspProcess, kafkaConfig: KafkaConfig,
                        config: Configuration = new Configuration(),
                        processVersion: ProcessVersion = ProcessVersion.empty,
                        parallelism: Int = 1): JobExecutionResult = {

      val creator: ProcessConfigCreator = prepareCreator(Nil, kafkaConfig)
      invoke(process, creator, config, processVersion, parallelism)
    }
  }


}


