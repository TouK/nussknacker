package pl.touk.nussknacker.test

import org.apache.commons.io.IOUtils

import java.io.{File, FileOutputStream, InputStream}

object MiscUtils {

  implicit class InputStreamOps(val in: InputStream) extends AnyVal {

    def toFile: File = {
      val tempFile = File.createTempFile("Nussknacker", null)
      tempFile.deleteOnExit()
      val out = new FileOutputStream(tempFile);
      IOUtils.copy(in, out);
      tempFile
    }

  }

}
