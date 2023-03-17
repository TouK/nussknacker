package pl.touk.nussknacker.engine.util

import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

object ResourceLoader {

  def load(path: String): String =
    IOUtils.resourceToString(path, StandardCharsets.UTF_8)

  def load(path: Path): String =
    load(path.toFile)

  def load(file: File): String =
    FileUtils.readFileToString(file, StandardCharsets.UTF_8)

}
