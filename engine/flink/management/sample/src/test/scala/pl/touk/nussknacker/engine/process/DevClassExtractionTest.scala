package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData

class DevClassExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = LocalModelData(ConfigFactory.load(), new DevProcessConfigCreator)
  protected override val outputResource = "/extractedTypes/devCreator.json"

}
