import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {MenuBar} from "../components/MenuBar"
import {VersionInfo} from "../components/versionInfo"
import {getFeatureSettings, getLoggedUser} from "../reducers/selectors/settings"
import {defaultsDeep, isEmpty} from "lodash"
import {Outlet} from "react-router-dom"
import {NkThemeProvider} from "./theme"
import {darkTheme} from "./darkTheme"
import {contentGetter} from "../windowManager"
import {WindowManagerProvider} from "@touk/window-manager"
import {Notifications} from "./Notifications"
import DragArea from "../components/DragArea"
import {Global} from "@emotion/react"

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
    <DragArea className={css({display: "flex"})}>
      <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
        <Notifications/>
        <WindowManagerProvider
          theme={darkTheme}
          contentGetter={contentGetter}
          className={css({flex: 1, display: "flex"})}
        >
          <NkThemeProvider>
            <Global styles={theme => ({
              ":root": {
                "--warnColor": theme.colors.warning,
                "--errorColor": theme.colors.error,
                "--successColor": theme.colors.sucess,
                "--infoColor": theme.colors.accent,
              },
            })}
            />
            <div
              id="app-container"
              className={css({
                width: "100%",
                height: "100%",
                display: "grid",
                gridTemplateColumns: "1fr",
                gridTemplateRows: "auto 1fr",
                alignItems: "stretch",
                header: {
                  overflow: "hidden",
                },
                main: {
                  overflow: "auto",
                  flexDirection: "column-reverse",
                },
              })}
            >
              <MenuBar/>
              <main>
                <VersionInfo/>
                <Outlet/>
                <UsageReportingImage/>
              </main>
            </div>
          </NkThemeProvider>
        </WindowManagerProvider>
      </NkThemeProvider>
    </DragArea>

  )
}

