import sbt.{taskKey, Compile, Global, Plugins, Setting}
import sbt.Keys.streams
import sbt.nio.Keys.{onChangedBuildSource, ReloadOnSourceChanges}
import scalafix.sbt.ScalafixPlugin
import utils.{getStagedScalaFiles, quoteSbtArgument, Step}

object LintStagedScalaFilesPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  override def requires: Plugins = ScalafixPlugin

  object autoImport {
    val lintStagedScalaFiles = taskKey[Unit]("Lint staged Scala files")
  }

  import autoImport.*

  override def projectSettings = Seq(
    lintStagedScalaFiles := {
      lintStagedScalaFilesOnly().value
    }
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )

  private def lintStagedScalaFilesOnly() = {
    val result = for {
      stagedFiles <- getStagedScalaFiles()
      stagedFilesToLint = onlyDesignerModuleScalaFiles(stagedFiles)
      _ <-
        if (stagedFilesToLint.nonEmpty) {
          callLintFiles(stagedFilesToLint)
        } else {
          Step.taskUnit
        }
    } yield ()
    result.runThrowing
  }

  private def callLintFiles(files: List[String]) = {
    for {
      _ <- Step.task {
        streams.map(_.log.info(s"Linting backend files ..."))
      }
      _ <- Step.task {
        (Compile / ScalafixPlugin.autoImport.scalafix)
          .toTask(files.map(f => s" --files ${quoteSbtArgument(f)}").mkString(""))
      }
    } yield ()
  }

  private def onlyDesignerModuleScalaFiles(files: List[String]) = {
    files
      .filter(_.contains("designer/server/"))
      .filter(_.endsWith(".scala"))
  }

}
