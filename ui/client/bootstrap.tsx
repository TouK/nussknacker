import {css} from "@emotion/css"
import {WindowManagerProvider} from "@touk/window-manager"
import {defaultsDeep} from "lodash"
import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import {Router} from "react-router-dom"
import ErrorBoundary from "./components/common/ErrorBoundary"
import DragArea from "./components/DragArea"
import LoaderSpinner from "./components/Spinner"
import {darkTheme} from "./containers/darkTheme"
import Notifications from "./containers/Notifications"
import {NkApp} from "./containers/NussknackerApp"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import {SettingsProvider} from "./containers/SettingsInitializer"
import {NkThemeProvider} from "./containers/theme"
import history from "./history"
import "./i18n"
import {StoreProvider} from "./store/provider"
import {contentGetter} from "./windowManager"

const rootContainer = document.createElement(`div`)
rootContainer.id = "root"
document.body.appendChild(rootContainer)

const Root = () => (
  <Suspense fallback={<LoaderSpinner show/>}>
    <ErrorBoundary>
      <DragArea className={css({display: "flex"})}>
        <StoreProvider>
          <Router history={history}>
            <SettingsProvider>
              <NussknackerInitializer>
                <Notifications/>
                <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
                  <WindowManagerProvider theme={darkTheme} contentGetter={contentGetter} className={css({flex: 1, display: "flex"})}>
                    <NkThemeProvider>
                      <NkApp/>
                    </NkThemeProvider>
                  </WindowManagerProvider>
                </NkThemeProvider>
              </NussknackerInitializer>
            </SettingsProvider>
          </Router>
        </StoreProvider>
      </DragArea>
    </ErrorBoundary>
  </Suspense>
)

ReactDOM.render(<Root/>, rootContainer)

export {store}
