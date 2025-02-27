import better.files.File
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import sbt.{taskKey, Def, Global, Level, Setting, State}
import sbt.Keys.{aggregate, onLoad, streams}
import sbt.internal.util.ManagedLogger

import scala.util.Using

object NuToolsGitPathsConfiguratorPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements

  lazy val configureNuToolsGitPaths = taskKey[Unit]("Configure Nu tools Git paths in the .git/config file")
  lazy val configureNuToolsGitPathsOnLoad: State => State = "configureNuToolsGitPaths" :: _

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    configureNuToolsGitPaths := configureNuToolsGitPathsTask.value,
  )

  override def globalSettings: Seq[Setting[_]] = Seq(
    Global / onLoad                      := (Global / onLoad).value andThen configureNuToolsGitPathsOnLoad,
    configureNuToolsGitPaths / aggregate := false
  )

  lazy val configureNuToolsGitPathsTask = Def.task {
    implicit val logger: ManagedLogger = streams.value.log
    updateGitConfigIfNeeded("core", "hooksPath", ".husky")
    updateGitConfigIfNeeded("blame", "ignoreRevsFile", ".git-blame-ignore-revs")
  }

  private def updateGitConfigIfNeeded(section: String, settingName: String, settingValue: String)(
      implicit logger: ManagedLogger
  ): Unit = {
    Using(FileRepositoryBuilder.create((File(System.getProperty("user.dir")) / ".git").toJava)) { repo =>
      val gitConfig = repo.getConfig
      Option(gitConfig.getString(section, null, settingName)) match {
        case Some(_) =>
        case None =>
          logger.log(
            Level.Info,
            s"Cannot find configured '$settingName' of [$section] section in .git/config file. Configuring the following value: '$settingValue' ..."
          )
          gitConfig.setString(section, null, settingName, settingValue)
          gitConfig.save()
      }
    }
  }

}
