import sbt.Keys._
import sbt._
import uk.co.randomcoding.sbt.WriteGitHooks

import java.nio.file.{Files, Path}

object NuGitHooksPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements

  lazy val writeGitHooks = taskKey[Unit]("Write Git Hooks")
  lazy val writeHooksOnLoad: State => State = "writeGitHooks" :: _

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    writeGitHooks := writeHooksTask.value,
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onLoad := (Global / onLoad).value andThen writeHooksOnLoad,
    writeGitHooks / aggregate := false
  )

  lazy val writeHooksTask = Def.task {
    val projectRootDirectory = Path.of(System.getProperty("user.dir"))
    val logger = streams.value.log
    val hooksSourceDir =
      Either.cond(
        Files.exists(projectRootDirectory / "git-hooks"),
        (projectRootDirectory / "git-hooks").toFile,
        "git-hooks directory doesn't exist!"
      )
    val hooksTargetDir =
      if (Files.exists(projectRootDirectory / ".git" / "hooks"))
        Right((projectRootDirectory / ".git" / "hooks").toFile)
      //support for git worktrees
      else if (Files.exists(projectRootDirectory.getParent / "hooks"))
        Right((projectRootDirectory.getParent / "hooks").toFile)
      else
        Left("target directory for git hooks doesn't exist. Make sure you have .git/hooks or ../hooks directory in your project")
    val writeGitHooks = for {
      sourceDir <- hooksSourceDir
      targetDir <- hooksTargetDir
    } yield WriteGitHooks(sourceDir, targetDir, logger)

    writeGitHooks.fold(s => logger.error(s), _ => ())
  }
}

