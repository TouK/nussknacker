import sbt.{taskKey, Compile, Global, Plugins, Setting}
import sbt.Keys.streams
import sbt.nio.Keys.{onChangedBuildSource, ReloadOnSourceChanges}
import scalafix.sbt.ScalafixPlugin
import utils.{getStagedScalaFiles, quoteSbtArgument, Step}

object CheckSqlSchemasInStagedScalaFilesPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  override def requires: Plugins = ScalafixPlugin

  object autoImport {
    val checkSqlSchemasInStagedScalaFiles =
      taskKey[Unit]("Check SQL schemas explicit usage in all SQL statements in staged Scala files")
  }

  import autoImport.*

  override def projectSettings = Seq(
    checkSqlSchemasInStagedScalaFiles := {
      checkStagedScalaFilesOnly().value
    }
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )

  private def checkStagedScalaFilesOnly() = {
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
        streams.map(_.log.info(s"Checking explicit usage of SQL schemas in staged files ..."))
      }
      _ <- Step.task {
        val rulesArg  = "--rules=class:NoSlickTableOrPlainSqlWithoutSchema"
        val filesArgs = files.map(f => s"--files ${quoteSbtArgument(f)}").mkString(" ")
        (Compile / ScalafixPlugin.autoImport.scalafix).toTask(s" $rulesArg $filesArgs")
      }
    } yield ()
  }

  private def onlyDesignerModuleScalaFiles(files: List[String]) = {
    files
      .filter(_.contains("designer/server/src/main/"))
      .filter(_.endsWith(".scala"))
  }

}
