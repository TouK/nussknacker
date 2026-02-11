package pl.touk.nussknacker.engine.assembly

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.assembly.UberJarAssembler._

import java.io.{InputStream, OutputStream}
import java.nio.file.Path
import java.security.{DigestInputStream, MessageDigest}
import java.util.HexFormat
import java.util.jar.{JarEntry, JarFile, JarOutputStream, Manifest}
import scala.collection.mutable
import scala.io.Source
import scala.util.Using

object JarEntryWriter extends LazyLogging {

  def writeManifest(manifest: Manifest, outputJar: JarOutputStream): Unit =
    outputJar.writeEntry(JarFile.MANIFEST_NAME) { outputJar =>
      manifest.write(outputJar)
    }

  def write(
      strategy: MergeStrategy,
      entryName: String,
      entryJarPath: Path,
      openEntryStream: () => InputStream,
      outputJar: JarOutputStream,
      state: AssemblyState
  ): Unit = strategy match {
    case MergeStrategy.Discard     => logger.trace(s"[Discard] Skipped entry '$entryName' from '$entryJarPath'.")
    case MergeStrategy.Deduplicate => writeWithoutDuplicates(entryName, entryJarPath, openEntryStream, outputJar, state)
    case MergeStrategy.FilterDistinctLines =>
      writeDistinctLinesToBuffer(entryName, entryJarPath, openEntryStream, state)
    case MergeStrategy.First  => writeIfFirst(entryName, entryJarPath, openEntryStream, outputJar, state)
    case MergeStrategy.Rename => writeRenamed(entryName, entryJarPath, openEntryStream, outputJar, state)
  }

  def writeBufferedEntries(
      state: AssemblyState,
      outputJar: JarOutputStream
  ): Unit = {
    val bufferedEntries = state.bufferedEntries
    if (bufferedEntries.nonEmpty) {
      logger.trace(s"[FilterDistinctLines] Writing ${bufferedEntries.size} buffered entries.")
      bufferedEntries.toSeq.sortBy(_._1).foreach { case (entryName, buffer) =>
        outputJar.writeArray(entryName, buffer.lines.mkString("\n").getBytes("UTF-8"))
        logger.trace(s"[FilterDistinctLines] Wrote entry '$entryName' (${buffer.lines.size} unique lines).")
      }
    }
  }

  private def writeRenamed(
      entryName: String,
      entryJarPath: Path,
      openEntryStream: () => InputStream,
      outputJar: JarOutputStream,
      state: AssemblyState
  ): Unit = {
    val jarName          = entryJarPath.getFileName.toString.stripSuffix(".jar")
    val changedEntryName = s"${entryName}_$jarName"
    state.writtenEntries.get(changedEntryName) match {
      case None =>
        outputJar.writeStream(changedEntryName, openEntryStream)
        state.writtenEntries(changedEntryName) = WrittenEntry(None, entryJarPath)
        logger.trace(s"[Rename] Wrote entry '$entryName' as '$changedEntryName' from '$entryJarPath'.")
      case Some(prev) =>
        throw new RuntimeException(
          s"Conflict: '$changedEntryName' produced by both '${prev.jarSource}' and '$entryJarPath'."
        )
    }
  }

  private def writeDistinctLinesToBuffer(
      entryName: String,
      entryJarPath: Path,
      openEntryStream: () => InputStream,
      state: AssemblyState
  ): Unit = {
    val lines = Using.resource(openEntryStream()) { entryStream =>
      Source.fromInputStream(entryStream, "UTF-8").getLines().toList
    }
    val buffer = state.bufferedEntries.getOrElseUpdate(
      entryName,
      BufferedEntry(mutable.LinkedHashSet.empty)
    )
    lines.foreach(buffer.lines.add)
    logger.trace(
      s"[FilterDistinctLines] Buffered entry '$entryName' from '$entryJarPath'."
    )
  }

  private def writeWithoutDuplicates(
      entryName: String,
      entryJarPath: Path,
      openEntryStream: () => InputStream,
      outputJar: JarOutputStream,
      state: AssemblyState
  ): Unit = {
    state.writtenEntries.get(entryName) match {
      case None => {
        val hash = outputJar.writeStreamThroughHash(entryName, openEntryStream)
        state.writtenEntries(entryName) = WrittenEntry(Some(hash), entryJarPath)
        logger.trace(s"[Deduplicate] Wrote entry '$entryName' from '$entryJarPath'.")
      }
      case Some(written) => {
        val hash = computeHash(openEntryStream)
        written.hash match {
          case Some(previousHash) if hash == previousHash =>
            logger.trace(
              s"[Deduplicate] Skipped entry '$entryName' from '$entryJarPath' because it is identical to '${written.jarSource}'."
            )
          case Some(_) =>
            throw new RuntimeException(
              s"Conflict: entry '$entryName' has different content in ${written.jarSource} and $entryJarPath"
            )
          case None =>
            throw new IllegalStateException(
              s"Internal error: missing hash for entry with Deduplicate strategy: '$entryName' from '${written.jarSource}'."
            )
        }
      }
    }
  }

  private def computeHash(openEntryStream: () => InputStream) = {
    val messageDigest = MessageDigest.getInstance(digestAlgorithmForEntryUniqueness)
    Using.resource(openEntryStream()) { entryStream =>
      Using.resource(new DigestInputStream(entryStream, messageDigest)) { dis =>
        dis.transferTo(OutputStream.nullOutputStream())
      }
    }
    HexFormat.of().formatHex(messageDigest.digest())
  }

  private def writeIfFirst(
      entryName: String,
      entryJarPath: Path,
      openEntryStream: () => InputStream,
      outputJar: JarOutputStream,
      state: AssemblyState
  ): Unit = {
    if (!state.writtenEntries.contains(entryName)) {
      outputJar.writeStream(entryName, openEntryStream)
      state.writtenEntries(entryName) = WrittenEntry(None, entryJarPath)
      logger.trace(s"[First] Wrote entry '$entryName' from '$entryJarPath'.")
    } else {
      logger.trace(
        s"[First] Skipped entry '$entryName' from '$entryJarPath' because it was already written."
      )
    }
  }

  implicit class JarOutputStreamOps(outputJar: JarOutputStream) {

    // timestamp has to be constant for content hash to be deterministic
    val constantTimestamp = 0L

    def writeStream(entryName: String, openEntryStream: () => InputStream): Unit =
      writeEntry(entryName) { out =>
        Using.resource(openEntryStream()) { entryStream =>
          entryStream.transferTo(out)
        }
      }

    def writeArray(entryName: String, byteArray: Array[Byte]): Unit =
      writeEntry(entryName) { out =>
        out.write(byteArray)
      }

    def writeStreamThroughHash(entryName: String, openEntryStream: () => InputStream): String = {
      val messageDigest = MessageDigest.getInstance(digestAlgorithmForEntryUniqueness)
      outputJar.writeEntry(entryName) { out =>
        Using.resource(openEntryStream()) { entryStream =>
          Using.resource(new DigestInputStream(entryStream, messageDigest)) { digestInputStream =>
            digestInputStream.transferTo(out)
          }
        }
      }
      HexFormat.of().formatHex(messageDigest.digest())
    }

    def writeEntry(entryName: String)(write: OutputStream => _): Unit = {
      val entry = new JarEntry(entryName)
      entry.setTime(constantTimestamp)
      outputJar.putNextEntry(entry)
      write(outputJar)
      outputJar.closeEntry()
    }

  }

  private val digestAlgorithmForEntryUniqueness = "SHA-1"

}
