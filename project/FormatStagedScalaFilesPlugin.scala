import org.scalafmt.sbt.ScalafmtPlugin
import sbt.{taskKey, Compile, Global, Setting}
import sbt.Keys.*
import sbt.nio.Keys.{onChangedBuildSource, ReloadOnSourceChanges}
import utils.{getStagedScalaFiles, quoteSbtArgument, Step}

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

  private def callFormatFiles(files: List[String]) = {
    for {
      _ <- Step.task {
        streams.map(_.log.info("Formatting backend files ..."))
      }
      _ <- Step.task {
        (Compile / ScalafmtPlugin.autoImport.scalafmtOnly).toTask(s" ${files.map(quoteSbtArgument).mkString(" ")}")
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
