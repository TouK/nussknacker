package pl.touk.nussknacker.engine.assembly

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.assembly.UberJarAssembler._

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.{DigestOutputStream, MessageDigest}
import java.util.HexFormat
import java.util.jar.{Attributes, JarFile, JarOutputStream, Manifest}
import scala.collection.mutable
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.Using

class UberJarAssembler(
    val mergeRules: List[MergeRule],
    val uberJarPrefix: String
) extends LazyLogging {

  private val mergeRuleMatcher = new MergeRuleResolver(mergeRules)

  def buildJar(
      outputDir: Path,
      jarFiles: List[Path],
      mainClass: String
  ): Path = {
    Files.createDirectories(outputDir)
    val tempFile = Files.createTempFile(outputDir, uberJarPrefix, ".jar.tmp")
    try {
      val hash: String = buildJarAndComputeHash(jarFiles, mainClass, tempFile)
      val finalPath    = outputDir.resolve(s"$uberJarPrefix-$hash.jar")
      Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING)
      finalPath
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  private def buildJarAndComputeHash(jarFiles: List[Path], mainClass: String, tempFile: Path) = {
    val digest   = MessageDigest.getInstance(digestAlgorithmForFileName)
    val manifest = buildManifest(mainClass)
    val state = AssemblyState(
      writtenEntries = mutable.Map.empty[String, WrittenEntry],
      bufferedEntries = mutable.Map.empty[String, BufferedEntry]
    )
    Using.resource(Files.newOutputStream(tempFile)) { fos =>
      Using.resource(new DigestOutputStream(fos, digest)) { dos =>
        Using.resource(new JarOutputStream(dos)) { outputJar =>
          JarEntryWriter.writeManifest(manifest, outputJar)
          jarFiles.foreach(mergeJarIntoUberJar(_, outputJar, state))
          JarEntryWriter.writeBufferedEntries(state, outputJar)
        }
      }
    }
    HexFormat.of().formatHex(digest.digest())
  }

  private def buildManifest(mainClass: String) = {
    val manifest = new Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    manifest.getMainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass)
    manifest
  }

  private def mergeJarIntoUberJar(
      jarPath: Path,
      outputJar: JarOutputStream,
      state: AssemblyState
  ): Unit = {
    Using.resource(new JarFile(jarPath.toFile)) { jar =>
      val entries = jar
        .entries()
        .asScala
        .filterNot(_.isDirectory)
        .toList
        .sortBy(_.getName)

      entries.foreach { entry =>
        val entryName       = entry.getName
        val strategy        = mergeRuleMatcher.strategyFor(entryName)
        val openEntryStream = () => jar.getInputStream(entry)
        JarEntryWriter.write(strategy, entryName, jarPath, openEntryStream, outputJar, state)
      }
    }
  }

}

object UberJarAssembler {

  private[assembly] case class WrittenEntry(
      hash: Option[String],
      jarSource: Path
  )

  private[assembly] case class BufferedEntry(
      lines: mutable.LinkedHashSet[String]
  )

  private[assembly] case class AssemblyState(
      writtenEntries: mutable.Map[String, WrittenEntry],
      bufferedEntries: mutable.Map[String, BufferedEntry]
  )

  private val digestAlgorithmForFileName = "SHA-1"

}
