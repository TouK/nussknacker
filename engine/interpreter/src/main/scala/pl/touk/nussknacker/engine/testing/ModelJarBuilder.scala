package pl.touk.nussknacker.engine.testing

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.{JarEntry, JarOutputStream}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

import scala.reflect.ClassTag
import scala.util.Using

object ModelJarBuilder {

  //mainly for testing
  def buildJarWithConfigCreator[T<:ProcessConfigCreator:ClassTag](outputFile: File = Files.createTempFile("creator", ".jar").toFile) : File = {
    val output = new FileOutputStream(outputFile)
    Using.resource(new JarOutputStream(output)) { jarOutput =>
      putToJar(
        jarOutput,
        "META-INF/services/pl.touk.nussknacker.engine.api.process.ProcessConfigCreator",
        implicitly[ClassTag[T]].runtimeClass.getName.getBytes(StandardCharsets.UTF_8)
      )
    }
    outputFile
  }


  private def putToJar(jarOutputStream: JarOutputStream, name: String, bytes: Array[Byte]) = {
    val entry = new JarEntry(name)
    jarOutputStream.putNextEntry(entry)
    jarOutputStream.write(bytes)
  }

}

