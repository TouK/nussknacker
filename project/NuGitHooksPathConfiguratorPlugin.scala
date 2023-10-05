import better.files.File
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import sbt.Keys.{aggregate, onLoad, streams}
import sbt.internal.util.ManagedLogger
import sbt.{Def, Global, Level, Setting, State, taskKey}

import scala.util.Using

object NuGitHooksPathConfiguratorPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements

  lazy val configureGitHooksPath                       = taskKey[Unit]("Configure Nu Git Hooks path")
  lazy val configureGitHooksPathOnLoad: State => State = "configureGitHooksPath" :: _

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    configureGitHooksPath := configureGitHookPathTask.value,
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onLoad                   := (Global / onLoad).value andThen configureGitHooksPathOnLoad,
    configureGitHooksPath / aggregate := false
  )

  lazy val configureGitHookPathTask = Def.task {
    implicit val logger: ManagedLogger = streams.value.log
    updateGitConfigHookPathIfNeeded(".husky")
  }

  private def updateGitConfigHookPathIfNeeded(hooksPath: String)(implicit logger: ManagedLogger): Unit = {
    Using(FileRepositoryBuilder.create((File(System.getProperty("user.dir")) / ".git").toJava)) { repo =>
      val gitConfig = repo.getConfig
      Option(gitConfig.getString("core", null, "hooksPath")) match {
        case Some(_) =>
        case None =>
          logger.log(
            Level.Info,
            s"Cannot find configured 'hooksPath' in .git/config. Configuring the following path: '$hooksPath' ..."
          )
          gitConfig.setString("core", null, "hooksPath", hooksPath)
          gitConfig.save()
      }
    }
  }

}
