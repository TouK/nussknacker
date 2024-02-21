package pl.touk.nussknacker.experimental

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.table.generic.GenericTableComponentProvider

class GenericTableApiDebug extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  test("debugging test") {
    val emptyConfig = ConfigFactory.empty()
    val components =
      new GenericTableComponentProvider().create(emptyConfig, ProcessObjectDependencies.withConfig(emptyConfig))

    1 shouldBe 1
  }

}
