package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.flink.test.ClassDiscoveryBaseTest
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData

class DevClassDiscoveryTest extends ClassDiscoveryBaseTest {

  protected override val model: ModelData =
    LocalModelData(ConfigFactory.load(), List.empty, configCreator = new DevProcessConfigCreator)

  val classes                           = model.modelDefinitionWithClasses.classDefinitions
  protected override val outputResource = "/extractedTypes/devCreator.json"

}
