import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {MenuBar} from "../components/MenuBar"
import {VersionInfo} from "../components/versionInfo"
import {getFeatureSettings, getLoggedUser} from "../reducers/selectors/settings"
import {isEmpty} from "lodash"
import {Outlet} from "react-router-dom"
import {GlobalCSSVariables, NkThemeProvider} from "./theme"
import {darkTheme} from "./darkTheme"
import {contentGetter} from "../windowManager"
import {WindowManagerProvider} from "@touk/window-manager"
import {Notifications} from "./Notifications"

function UsageReportingImage() {
  const featuresSettings = useSelector(getFeatureSettings)
  return featuresSettings.usageStatisticsReports.enabled && (
    <img
      src={featuresSettings.usageStatisticsReports.url}
      alt="anonymous usage reporting"
      referrerPolicy="origin"
      hidden
    />
  )
}

export function NussknackerApp() {
  const loggedUser = useSelector(getLoggedUser)

  if (isEmpty(loggedUser)) {
    return null
  }

  return (
    <NkThemeProvider theme={darkTheme}>
      <GlobalCSSVariables/>
      <WindowManagerProvider
        theme={darkTheme}
        contentGetter={contentGetter}
        className={css({flex: 1, display: "flex"})}
      >
        <div
          id="app-container"
          className={css({
            flex: 1,
            display: "grid",
            gridTemplateRows: "auto 1fr",
            alignItems: "stretch",
          })}
        >
          <MenuBar/>
          <main className={css({overflow: "auto"})}>
            <Outlet/>
          </main>
        </div>
      </WindowManagerProvider>
      <Notifications/>
      <VersionInfo/>
      <UsageReportingImage/>
    </NkThemeProvider>
  )
}

