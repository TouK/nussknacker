package pl.touk.nussknacker.engine.management

import java.io.File
import java.nio.file.Files
import java.util.Collections

import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.commons.io.FileUtils
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConversions._

class FlinkProcessConfigLocationTest extends FunSuite with DockerTest with Matchers {


  //FIXME: do we still need it??
  ignore("reads config from local dir") {
    processManager.findJobStatus("aaa").futureValue shouldBe None
  }

  override lazy val config: Config = {
    val normalConf = super.config

    val temp = Files.createTempDirectory("flinkConfig").toFile

    writeConfigAsFlinkConfYaml(normalConf, temp)

    normalConf
      .withValue("flinkConfig.configLocation", ConfigValueFactory.fromAnyRef(temp.getAbsolutePath))
      .withValue("flinkConfig.customConfig", ConfigValueFactory.fromMap(Collections.emptyMap()))
  }

  private def writeConfigAsFlinkConfYaml(normalConf: Config, tempDir :File) : Unit = {

    val confYml = normalConf.getConfig("flinkConfig.customConfig").entrySet().toList.map { entry =>
      s"${entry.getKey}: ${entry.getValue.render().replace("\"", "")}"
    }.mkString("\n")

    FileUtils.write(new File(tempDir, "flink-conf.yaml"), confYml)
  }
}
