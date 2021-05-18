package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}

import java.net.URI
import java.nio.file.Files
import scala.jdk.CollectionConverters.mapAsJavaMapConverter

class ConfigFactoryExtSpec extends FunSuite with Matchers {

  test("loads in correct order") {

    val conf1 = writeToTemp(Map("f1" -> "default", "f2" ->"not so default"))
    val conf2 = writeToTemp(Map("f1" -> "I win!"))

    val result = ConfigFactoryExt.load(List(conf1, conf2, "classpath:someConfig.conf").mkString(", "), getClass.getClassLoader)

    result.getString("f1") shouldBe "I win!"
    result.getString("f2") shouldBe "not so default"
    result.getString("f4") shouldBe "fromClasspath"
    result.hasPath("f5") shouldBe false

  }

  def writeToTemp(map: Map[String, Any]): URI = {
    val temp = Files.createTempFile("ConfigFactoryExt", ".conf")
    temp.toFile.deleteOnExit()
    Files.write(temp, ConfigFactory.parseMap(map.asJava).root().render().getBytes)
    temp.toUri
  }

}
