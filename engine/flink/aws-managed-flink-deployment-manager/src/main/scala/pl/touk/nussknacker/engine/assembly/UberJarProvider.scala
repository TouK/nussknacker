package pl.touk.nussknacker.engine.assembly

import java.net.URL
import java.nio.file.{Files, Path}
import scala.reflect.io.Directory

class UberJarProvider(
    modelUrls: List[URL],
    mergeRules: List[MergeRule],
    mainClass: String,
    uberJarPrefix: String
) {

  private var jarFile: Option[Path] = None

  private val modelJarBuilder = new UberJarAssembler(mergeRules, uberJarPrefix)

  def getUberJar(): Path = synchronized {
    jarFile match {
      case Some(file) if Files.exists(file) => file
      case _ =>
        val file = buildJar()
        jarFile = Some(file)
        file
    }
  }

  private def buildJar(): Path = {
    val outputDir = createTempOutputDir()
    val jarPaths  = modelUrls.map { resolvePath(_, outputDir) }
    modelJarBuilder.buildJar(
      outputDir = outputDir,
      jarFiles = jarPaths,
      mainClass = mainClass
    )
  }

  private def createTempOutputDir(): Path = {
    val tempDir = Files.createTempDirectory("nussknacker-assembled-jars")
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = new Directory(tempDir.toFile).deleteRecursively()
    })
    tempDir
  }

  private def resolvePath(url: URL, tempDir: Path): Path =
    if (url.getProtocol == "file") {
      Path.of(url.toURI)
    } else {
      throw new UnsupportedOperationException("Non-file jar URL's are not supported")
    }

}
