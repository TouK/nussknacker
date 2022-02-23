package pl.touk.nussknacker.engine.kafka.signal

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.signal.CustomSignalReader.signalTopic
import pl.touk.nussknacker.engine.process.helpers.SampleNodes
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord, TransformerWithTime}

class KafkaSignalsCreator(data: List[SimpleRecord]) extends EmptyProcessConfigCreator {


  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "signalReader" -> WithCategories(CustomSignalReader),
    "transformWithTime" -> WithCategories(TransformerWithTime)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    "input" -> WithCategories(SampleNodes.simpleRecordSource(data))
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "logService" -> WithCategories(new MockService)
  )

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[TestProcessSignalFactory]] = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config)
    Map("sig1" -> WithCategories(new TestProcessSignalFactory(kafkaConfig, signalTopic)))
  }
}
