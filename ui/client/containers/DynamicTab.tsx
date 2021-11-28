import * as queryString from "query-string"
import React, {memo} from "react"
import ErrorBoundary from "../components/common/ErrorBoundary"
import {ExternalModule, splitUrl, useExternalLib} from "./ExternalLib"
import {ModuleString, ModuleUrl} from "./ExternalLib/types"
import {MuiThemeProvider} from "./muiThemeProvider"
import {Redirect} from "react-router"
import NotFound from "./errors/NotFound"

export type DynamicTabData = {
  title: string,
  id: string,
  // expected:
  //  * url of working app - to include in iframe
  //  * url ({module}/{path}@{host}/{remoteEntry}.js) of hosted remoteEntry js file (module federation) with default exported react component - included as component
  //  * url of internal route in NK
  url: string,
  requiredPermission?: string,
  type: "Local" | "IFrame" | "Remote",
}

const RemoteTabComponent = ({scope, basepath}: {scope: ModuleString, basepath?: string}) => {
  const {module: {default: Component}} = useExternalLib(scope)
  return <Component basepath={basepath}/>
}

const RemoteModuleTab = (props: {url: ModuleUrl, basepath?: string}) => {
  const [url, scope] = splitUrl(props.url)
  return (
    <ErrorBoundary FallbackComponent={() => <NotFound/>}>
      <MuiThemeProvider>
        <ExternalModule url={url}>
          <RemoteTabComponent scope={scope} basepath={props.basepath}/>
        </ExternalModule>
      </MuiThemeProvider>
    </ErrorBoundary>
  )
}

const IframeTab = ({url}: {url: string}) => (
  <iframe
    src={queryString.stringifyUrl({url: url, query: {iframe: true}})}
    width="100%"
    height="100%"
    frameBorder="0"
  />
)

export const DynamicTab = memo(function DynamicComponent({tab, basepath}: {tab: DynamicTabData, basepath?: string}): JSX.Element {
  switch (tab.type) {
    case "Remote": return <RemoteModuleTab url={tab.url} basepath={basepath}/>
    case "Local": return <Redirect to={tab.url}/>
    case "IFrame": return <IframeTab url={tab.url}/>
  }
})
