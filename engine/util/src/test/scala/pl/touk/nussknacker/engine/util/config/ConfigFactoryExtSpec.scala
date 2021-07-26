package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}

import java.net.URI
import java.nio.file.Files
import scala.jdk.CollectionConverters.mapAsJavaMapConverter

class ConfigFactoryExtSpec extends FunSuite with Matchers {

  test("loads in correct order") {

    val conf1 = writeToTemp(Map("f1" -> "default", "f2" ->"not so default", "akka.http.server.request-timeout" -> "300s"))
    val conf2 = writeToTemp(Map("f1" -> "I win!"))

    val result = ConfigFactoryExt.parseConfigFallbackChain(List(conf1, conf2, URI.create("classpath:someConfig.conf")), getClass.getClassLoader)

    result.getString("f1") shouldBe "I win!"
    result.getString("f2") shouldBe "not so default"
    result.getString("f4") shouldBe "fromClasspath"
    result.hasPath("f5") shouldBe false
    result.getString("akka.http.server.request-timeout") shouldBe "300s"
  }

  def writeToTemp(map: Map[String, Any]): URI = {
    val temp = Files.createTempFile("ConfigFactoryExt", ".conf")
    temp.toFile.deleteOnExit()
    Files.write(temp, ConfigFactory.parseMap(map.asJava).root().render().getBytes)
    temp.toUri
  }

}
