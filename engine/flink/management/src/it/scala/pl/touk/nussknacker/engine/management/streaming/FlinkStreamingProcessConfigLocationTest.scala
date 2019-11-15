package pl.touk.nussknacker.engine.management.streaming

import java.io.File
import java.nio.file.Files
import java.util.Collections

import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.commons.io.FileUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.collection.JavaConverters._

class FlinkStreamingProcessConfigLocationTest extends FunSuite with StreamingDockerTest with Matchers {

  //FIXME: do we still need it??
  ignore("reads config from local dir") {
    processManager.findJobStatus(ProcessName("aaa")).futureValue shouldBe None
  }

  override protected def classPath: String = ""

  override lazy val config: Config = {
    val normalConf = super.config

    val temp = Files.createTempDirectory("flinkConfig").toFile

    writeConfigAsFlinkConfYaml(normalConf, temp)

    normalConf
      .withValue("flinkConfig.configLocation", ConfigValueFactory.fromAnyRef(temp.getAbsolutePath))
      .withValue("flinkConfig.customConfig", ConfigValueFactory.fromMap(Collections.emptyMap()))
  }

  private def writeConfigAsFlinkConfYaml(normalConf: Config, tempDir :File) : Unit = {

    val confYml = normalConf.getConfig("flinkConfig.customConfig").entrySet().asScala.toList.map { entry =>
      s"${entry.getKey}: ${entry.getValue.render().replace("\"", "")}"
    }.mkString("\n")

    FileUtils.write(new File(tempDir, "flink-conf.yaml"), confYml)
  }
}
