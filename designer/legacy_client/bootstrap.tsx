import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import {Router} from "react-router-dom"
import ErrorBoundary from "./components/common/ErrorBoundary"
import LoaderSpinner from "./components/Spinner"
import {Notifications} from "./containers/Notifications"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import {SettingsProvider} from "./containers/SettingsInitializer"
import {NkThemeProvider} from "./containers/theme"
import history from "./history"
import "./i18n"
import {StoreProvider} from "./store/provider"
import ProcessTabs from "./containers/ProcessTabs"

const rootContainer = document.createElement(`div`)
rootContainer.id = "root"
document.body.appendChild(rootContainer)

const Root = () => (
  <Suspense fallback={<LoaderSpinner show/>}>
    <ErrorBoundary>
      <StoreProvider>
        <Router history={history}>
          <SettingsProvider>
            <NussknackerInitializer>
              <Notifications/>
              <NkThemeProvider>
                <ProcessTabs/>
              </NkThemeProvider>
            </NussknackerInitializer>
          </SettingsProvider>
        </Router>
      </StoreProvider>
    </ErrorBoundary>
  </Suspense>
)

ReactDOM.render(<Root/>, rootContainer)
