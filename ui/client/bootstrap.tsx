import {WindowManagerProvider} from "@touk/window-manager"
import {defaultsDeep} from "lodash"
import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import {Provider} from "react-redux"
import {Router} from "react-router-dom"
import {PersistGate} from "redux-persist/integration/react"
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
import configureStore from "./store/configureStore"
import {contentGetter} from "./windowManager"
import {VersionInfo} from "./components/versionInfo"

const {store, persistor} = configureStore()
const rootContainer = document.createElement(`div`)
rootContainer.id = "root"
document.body.appendChild(rootContainer)

const Root = () => (
  <Suspense fallback={<LoaderSpinner show/>}>
    <ErrorBoundary>
      <Provider store={store}>
        <DragArea>
          <PersistGate loading={null} persistor={persistor}>
            <Router history={history}>
              <SettingsProvider>
                <NussknackerInitializer>
                  <Notifications/>
                  <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
                    <WindowManagerProvider theme={darkTheme} contentGetter={contentGetter}>
                      <NkThemeProvider>
                        <VersionInfo/>
                        <NkApp/>
                      </NkThemeProvider>
                    </WindowManagerProvider>
                  </NkThemeProvider>
                </NussknackerInitializer>
              </SettingsProvider>
            </Router>
          </PersistGate>
        </DragArea>
      </Provider>
    </ErrorBoundary>
  </Suspense>
)

ReactDOM.render(<Root/>, rootContainer)

export {store}
