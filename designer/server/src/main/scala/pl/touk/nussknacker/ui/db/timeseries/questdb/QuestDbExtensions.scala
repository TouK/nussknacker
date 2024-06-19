package pl.touk.nussknacker.ui.db.timeseries.questdb

import better.files.File
import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.sql.{Record, RecordCursorFactory}
import io.questdb.griffin.SqlExecutionContext
import io.questdb.log.LogFactory

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import scala.collection.mutable.ArrayBuffer
import scala.util.{Try, Using}

private[questdb] trait QuestDbExtensions {

  implicit class RecordCursorFactoryExtension(factory: RecordCursorFactory) {

    def fetch[T](sqlContext: SqlExecutionContext)(mapper: Record => T): List[T] =
      Using.resource(factory.getCursor(sqlContext)) { recordCursor =>
        val buffer = ArrayBuffer.empty[T]
        val record = recordCursor.getRecord
        while (recordCursor.hasNext) {
          val entry = mapper(record)
          buffer.append(entry)
        }
        buffer.toList
      }

  }

  implicit class CairoEngineExtension(engine: CairoEngine) extends LazyLogging {

    def deleteRootDir(): File = {
      val nuDir = File(engine.getConfiguration.getRoot)
      if (nuDir.exists) {
        Try(nuDir.delete())
      }
      nuDir
    }

  }

  implicit class BuildCairoEngineExtension(rootDir: File) {
    def createDirIfNotExists(): File =
      rootDir.createDirectories()

    def configureLogging(): File = {
      rootDir
        .createChild("conf/log.conf", createParents = true)
        .writeText(
          """
            |writers=stdout
            |w.stdout.class=io.questdb.log.LogConsoleWriter
            |w.stdout.level=ERROR
            |""".stripMargin
        )(Seq(StandardOpenOption.TRUNCATE_EXISTING), StandardCharsets.UTF_8)
      LogFactory.configureRootDir(rootDir.canonicalPath)
      rootDir
    }

  }

}

object QuestDbExtensions extends QuestDbExtensions
