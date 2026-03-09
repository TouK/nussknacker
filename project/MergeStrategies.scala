import sbt.*
import sbtassembly.MergeStrategy

import java.io.File

object MergeStrategies {

  // source: https://github.com/sbt/sbt-assembly/blob/v1.1.0/src/main/scala/sbtassembly/AssemblyUtils.scala#L20
  private val PathRE = "([^/]+)/(.*)".r

  def sourceOfFileForMerge(tempDir: File, f: File): (File, Boolean) = {
    val baseURI            = tempDir.getCanonicalFile.toURI
    val otherURI           = f.getCanonicalFile.toURI
    val relative           = baseURI.relativize(otherURI)
    val PathRE(head, tail) = relative.getPath

    if ((tempDir / (head + ".jarName")).exists) {
      val jarName = IO.read(tempDir / (head + ".jarName"), IO.utf8)
      (new File(jarName), true)
    } else {
      val dirName = IO.read(tempDir / (head + ".dir"), IO.utf8)
      (new File(dirName), false)
    } // if-else
  }

  // TODO: update sbt-assembly and replace with simpler CustomMergeStrategy from nussknacker-enterprise-extensions
  def onlyFromArtifact(artifactId: String): MergeStrategy = new MergeStrategy {
    override val name: String = s"includeFromArtifact_$artifactId"

    override def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
      val fileNamePrefix = artifactId + "-"
      val conflicts      = files.map(sourceOfFileForMerge(tempDir, _))
      val includedFiles = files.zip(conflicts).flatMap { case (f, (source, isFromJar)) =>
        if (isFromJar && source.getName.startsWith(artifactId)) Some(f -> path)
        else None
      }
      if (includedFiles.size == 1) {
        Right(includedFiles)
      } else {
        val conflictsPrettyPrinted = conflicts.map(conflict => s"  $conflict").mkString("\n")
        Left(s"Could not find the expected library: '$artifactId' in the merge conflicts:\n$conflictsPrettyPrinted")
      }
    }
  }

}
