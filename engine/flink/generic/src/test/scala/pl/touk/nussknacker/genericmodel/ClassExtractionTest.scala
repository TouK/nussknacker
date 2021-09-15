package pl.touk.nussknacker.genericmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.ObjectEncoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax.EncoderOps
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.testing.LocalModelData

class ClassExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = {
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("notused:1111"))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("notused:1111"))
    LocalModelData(config, new GenericConfigCreator)
  }
  protected override val outputResource = "/extractedTypes/genericCreator.json"

}

