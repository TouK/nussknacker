import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {MenuBar} from "../components/MenuBar"
import ProcessBackButton from "../components/Process/ProcessBackButton"
import {VersionInfo} from "../components/versionInfo"
import {getFeatureSettings, getLoggedUser} from "../reducers/selectors/settings"
import * as Paths from "./paths"
import {EnvironmentTag} from "./EnvironmentTag"
import {isEmpty} from "lodash"
import {RootRoutes} from "./RootRoutes"

export function NussknackerApp() {
  const featuresSettings = useSelector(getFeatureSettings)
  const loggedUser = useSelector(getLoggedUser)

  if (isEmpty(loggedUser)) {
    return null
  }

  return (
    <div
      id="app-container"
      className={css({
        width: "100%",
        height: "100%",
        display: "grid",
        alignItems: "stretch",
        gridTemplateRows: "auto 1fr",
        main: {
          overflow: "auto",
          display: "flex",
          flexDirection: "column-reverse",
        },
      })}
    >
      <MenuBar
        appPath={Paths.RootPath}
        leftElement={<ProcessBackButton/>}
        rightElement={<EnvironmentTag/>}
      />
      <main>
        <VersionInfo/>
        <RootRoutes/>
      </main>
      {featuresSettings.usageStatisticsReports.enabled && (
        <img
          src={featuresSettings.usageStatisticsReports.url}
          alt="anonymous usage reporting"
          referrerPolicy="origin"
          hidden
        />
      )}
    </div>
  )
}

