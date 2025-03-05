package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.net.URL
import java.util.UUID

import CustomFicusInstances._

class CustomFicusInstancesSpec extends AnyFlatSpec with Matchers {

  it should "decode URL using custom decoder" in {
    case class Container(url: URL)

    val config = ConfigFactory.empty().withValue("url", fromAnyRef("/absolute/path"))

    config.as[Container].url shouldBe new File("/absolute/path").toURI.toURL
  }

  it should "decode UUID" in {
    case class Container(uuid: UUID)

    val config = ConfigFactory.empty().withValue("uuid", fromAnyRef("a0cddbd7-0f2d-4c86-8434-8811c45f18dd"))

    config.as[Container].uuid shouldBe UUID.fromString("a0cddbd7-0f2d-4c86-8434-8811c45f18dd")
  }

}
