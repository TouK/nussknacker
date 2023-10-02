import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys._
import sbt.nio.Keys.{ReloadOnSourceChanges, onChangedBuildSource}
import sbt.{Compile, Def, File, Global, IO, Setting, taskKey}
import utils.Step

import scala.util.{Failure, Success, Try}

object FormatStagedScalaFilesPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    val formatStagedScalaFiles = taskKey[Unit]("Format staged Scala files")
  }

  import autoImport._

  override def projectSettings = Seq(
    formatStagedScalaFiles in Global := {
      formatStagedScalaFilesOnly().value
    }
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )

  private def formatStagedScalaFilesOnly() = {
    val result = for {
      stagedFiles <- getStagedScalaFiles()
      _ <-
        if (stagedFiles.nonEmpty) {
          for {
            _ <- callFormatFiles(stagedFiles)
            _ <- addToGitAllStagedFilesOnceAgain(stagedFiles)
          } yield ()
        } else {
          Step.taskUnit
        }
    } yield ()
    result.runThrowing
  }

  private def getStagedScalaFiles() = Step.deferredTask {
    os
      .proc("git", "diff", "--cached", "--name-only", "--diff-filter=ACM")
      .call()
      .out
      .lines()
      .filter(f => f.endsWith(".scala") || f.endsWith(".sbt"))
      .toList
  }

  private def callFormatFiles(files: List[String]) = {
    for {
      _ <- Step.task {
        streams.map(_.log.info("Formatting backend files ..."))
      }
      // todo: improve
      resolvedFiles <- Step.task {
        Def.task {
          files.map { file =>
            Try(IO.resolve(baseDirectory.value, new File(file))) match {
              case Failure(e) =>
                streams.value.log.error(s"Error with file: $e")
                throw e
              case Success(file) =>
                file.getAbsolutePath
            }
          }

        }
      }
      _ <- Step.task {
        (Compile / ScalafmtPlugin.autoImport.scalafmtOnly).toTask(s" ${resolvedFiles.mkString(" ")}")
      }
    } yield ()

  }

  private def addToGitAllStagedFilesOnceAgain(scalaStagedFiles: List[String]) = Step.deferredTask {
    os
      .proc("git" :: "add" :: scalaStagedFiles)
      .call()
    ()
  }

}
