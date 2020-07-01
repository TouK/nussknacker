package pl.touk.nussknacker.engine.process.helpers

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.test.StoppableExecutionEnvironment
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.{FlinkStreamingProcessRegistrar, SimpleJavaEnum}
import pl.touk.nussknacker.engine.testing.LocalModelData

object ProcessTestHelpers {

  val signalTopic = "signals1"

  object processInvoker {

    def invokeWithSampleData(process: EspProcess,
                             data: List[SimpleRecord],
                             flinkConfiguration: Configuration = new Configuration(),
                             processVersion: ProcessVersion = ProcessVersion.empty,
                             parallelism: Int = 1): Unit = {
      val config = ConfigFactory.load()
        .withValue("kafka.kafkaAddress", fromAnyRef("http://notexist.pl"))
      val creator: ProcessConfigCreator = prepareCreator(data, config)

      val env = StoppableExecutionEnvironment(flinkConfiguration)
      try {
        FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
          .register(new StreamExecutionEnvironment(env), process, processVersion)

        MockService.clear()
        SinkForStrings.clear()
        SinkForInts.clear()
        val id = env.execute(process.id)
        env.waitForJobState(id.getJobID, process.id, ExecutionState.FINISHED)()
      } finally {
        env.stop()
      }
    }

    def invoke(process: EspProcess,
               creator: ProcessConfigCreator,
               config: Config,
               configuration: Configuration,
               processVersion: ProcessVersion,
               parallelism: Int, actionToInvokeWithJobRunning: => Unit): Unit = {
      val env = StoppableExecutionEnvironment(configuration)
      try {
        FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
          .register(new StreamExecutionEnvironment(env), process, processVersion)


        MockService.clear()
        env.withJobRunning(process.id)(actionToInvokeWithJobRunning)
      } finally {
        env.stop()
      }
    }

    def prepareCreator(data: List[SimpleRecord], config: Config): ProcessConfigCreator = new ProcessConfigCreator {

      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
        "logService" -> WithCategories(new MockService),
        "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService),
        "serviceAcceptingOptionalValue" -> WithCategories(ServiceAcceptingScalaOption)
      )

      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[FlinkSourceFactory[_]]] = Map(
        "input" -> WithCategories(SampleNodes.simpleRecordSource(data)),
        "intInputWithParam" -> WithCategories(new IntParamSourceFactory(new ExecutionConfig)),
        "kafka-keyvalue" -> WithCategories(new KeyValueKafkaSourceFactory(processObjectDependencies)),
        "genericParametersSource" -> WithCategories(GenericParametersSource)
      )

      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
        "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink)),
        "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts)),
        "sinkForStrings" -> WithCategories(SinkFactory.noParam(SinkForStrings)),
        "lazyParameterSink"-> WithCategories(LazyParameterSinkFactory),
        "eagerOptionalParameterSink"-> WithCategories(EagerOptionalParameterSinkFactory),
        "genericParametersSink" -> WithCategories(GenericParametersSink)
      )

      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
        "stateCustom" -> WithCategories(StateCustomNode),
        "customFilter" -> WithCategories(CustomFilter),
        "customFilterContextTransformation" -> WithCategories(CustomFilterContextTransformation),
        "customContextClear" -> WithCategories(CustomContextClear),
        "sampleJoin" -> WithCategories(CustomJoin),
        "joinBranchExpression" -> WithCategories(CustomJoinUsingBranchExpressions),
        "signalReader" -> WithCategories(CustomSignalReader),
        "transformWithTime" -> WithCategories(TransformerWithTime),
        "transformWithNullable" -> WithCategories(TransformerWithNullableParam),
        "optionalEndingCustom" -> WithCategories(OptionalEndingCustom),
        "genericParametersNode" -> WithCategories(GenericParametersNode)
      )

      override def listeners(processObjectDependencies: ProcessObjectDependencies) = List()

      override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
        ExceptionHandlerFactory.noParams(_ => RecordingExceptionHandler)


      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
        val dictId = EmbeddedDictDefinition.enumDictId(classOf[SimpleJavaEnum])
        val dictDef = EmbeddedDictDefinition.forJavaEnum(classOf[SimpleJavaEnum])
        val globalProcessVariables = Map(
          "processHelper" -> WithCategories(ProcessHelper),
          "enum" -> WithCategories(DictInstance(dictId, dictDef)),
          "typedMap" -> WithCategories(TypedMap(Map("aField" -> "123"))))
        ExpressionConfig(globalProcessVariables, List.empty, dictionaries = Map(dictId -> WithCategories(dictDef)))
      }

      override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[TestProcessSignalFactory]] = {
        val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
        Map("sig1" ->
          WithCategories(new TestProcessSignalFactory(kafkaConfig, signalTopic)))
      }

      override def buildInfo(): Map[String, String] = Map.empty
    }

    def invokeWithKafka(process: EspProcess,
                        config: Config,
                        flinkConfiguration: Configuration = new Configuration(),
                        processVersion: ProcessVersion = ProcessVersion.empty,
                        parallelism: Int = 1)(actionToInvokeWithJobRunning: => Unit): Unit = {
      val creator: ProcessConfigCreator = prepareCreator(Nil, config)
      invoke(process, creator, config, flinkConfiguration, processVersion, parallelism, actionToInvokeWithJobRunning)
    }
  }


}


