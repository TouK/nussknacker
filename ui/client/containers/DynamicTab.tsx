import * as queryString from "query-string"
import React, {memo} from "react"
import ErrorBoundary from "../components/common/ErrorBoundary"
import {ExternalModule, splitUrl, useExternalLib} from "./ExternalLib"
import {ModuleString, ModuleUrl} from "./ExternalLib/types"
import {MuiThemeProvider} from "./muiThemeProvider"
import {Redirect} from "react-router";

export type DynamicTabData = {
  title: string,
  id: string,
  // expected:
  //  * url of working app - to include in iframe
  //  * url ({module}/{path}@{host}/{remoteEntry}.js) of hosted remoteEntry js file (module federation) with default exported react component - included as component
  //  * url of internal route in NK
  url: string,
  requiredPermission?: string,
  type: "Local" | "IFrame" | "Remote"
}

const RemoteTabComponent = ({scope}: {scope: ModuleString}) => {
  const {module: {default: Component}} = useExternalLib(scope)
  return <Component/>
}

const RemoteModuleTab = (props: {url: ModuleUrl}) => {
  const [url, scope] = splitUrl(props.url)
  return (
    <MuiThemeProvider>
      <ExternalModule url={url}>
        <ErrorBoundary>
          <RemoteTabComponent scope={scope}/>
        </ErrorBoundary>
      </ExternalModule>
    </MuiThemeProvider>
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

export const DynamicTab = memo(function DynamicComponent({tab}: {tab: DynamicTabData}): JSX.Element {
  switch (tab.type) {
    case "Remote": return <RemoteModuleTab url={tab.url}/>
    case "Local": return <Redirect to={tab.url}/>
    case "IFrame": return <IframeTab url={tab.url}/>
  }
})
