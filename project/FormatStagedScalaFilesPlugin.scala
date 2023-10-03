import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys._
import sbt.nio.Keys.{ReloadOnSourceChanges, onChangedBuildSource}
import sbt.{Compile, Def, Global, Setting, inputKey, taskKey}
import utils.Step
import sbt.complete.Parsers.spaceDelimited

object FormatStagedScalaFilesPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    val formatStagedScalaFiles = taskKey[Unit]("Format staged Scala files")
    val formatGivenScalaFiles  = inputKey[Unit]("Format given Scala files")
  }

  import autoImport._

  override def projectSettings = Seq(
    formatStagedScalaFiles in Global := {
      formatStagedScalaFilesOnly().value
    },
    formatGivenScalaFiles := Def.inputTaskDyn {
      val files = spaceDelimited("<files>").parsed.toList
      Def.taskDyn {
        formatScalaFilesAndStageThem(files).runThrowing
      }
    }.evaluated
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )

  private def formatStagedScalaFilesOnly() = {
    val result = for {
      files <- getStagedScalaFiles()
      _     <- formatScalaFilesAndStageThem(files)
    } yield ()
    result.runThrowing
  }

  private def formatScalaFilesAndStageThem(files: List[String]) = {
    if (files.nonEmpty) {
      for {
        _ <- callFormatFiles(files)
        _ <- addToGitAllFilesOnceAgain(files)
      } yield ()
    } else {
      Step.taskUnit
    }
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
      _ <- Step.task {
        (Compile / ScalafmtPlugin.autoImport.scalafmtOnly).toTask(s" ${files.mkString(" ")}")
      }
    } yield ()
  }

  private def addToGitAllFilesOnceAgain(scalaStagedFiles: List[String]) = Step.deferredTask {
    os
      .proc("git" :: "add" :: scalaStagedFiles)
      .call()
    ()
  }

}
