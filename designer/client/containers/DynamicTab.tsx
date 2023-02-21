import * as queryString from "query-string"
import React, {memo, useMemo} from "react"
import ErrorBoundary from "../components/common/ErrorBoundary"
import {ExternalModule, splitUrl, useExternalLib} from "./ExternalLib"
import {ModuleString, ModuleUrl} from "./ExternalLib/types"
import {MuiThemeProvider} from "./muiThemeProvider"
import {NotFound} from "./errors/NotFound"
import SystemUtils from "../common/SystemUtils"
import ScopedCssBaseline from "@mui/material/ScopedCssBaseline"
import {useNavigate} from "react-router-dom"

export type DynamicTabData = {
  title: string,
  id: string,
  // expected:
  //  * url of working app - to include in iframe
  //  * url ({module}/{path}@{host}/{remoteEntry}.js) of hosted remoteEntry js file (module federation) with default exported react component - included as component
  //  * url of internal route in NK
  url: string,
  requiredPermission?: string,
  type: "Local" | "IFrame" | "Remote" | "Url",
  addAccessTokenInQueryParam?: boolean,
}

const RemoteTabComponent = <CP extends { basepath?: string }>({
  scope,
  componentProps,
}: { scope: ModuleString, componentProps: CP }) => {
  const {module: {default: Component}} = useExternalLib(scope)
  return <Component {...componentProps}/>
}

export const RemoteModuleTab = <CP extends { basepath?: string }>({
  url,
  componentProps,
}: { url: ModuleUrl, componentProps: CP }): JSX.Element => {
  const [urlValue, scope] = useMemo(() => splitUrl(url), [url])
  const navigate = useNavigate()
  const props = useMemo(() => ({onNavigate: navigate, ...componentProps}), [componentProps, navigate])

  return (
    <ErrorBoundary FallbackComponent={() => <NotFound/>}>
      <MuiThemeProvider>
        <ScopedCssBaseline style={{flex: 1, overflow: "hidden"}}>
          <ExternalModule url={urlValue}>
            <RemoteTabComponent scope={scope} componentProps={props}/>
          </ExternalModule>
        </ScopedCssBaseline>
      </MuiThemeProvider>
    </ErrorBoundary>
  )
}

const IframeTab = ({tab}: { tab: DynamicTabData }) => {
  const iframeQueryParam = {iframe: true}
  const accessTokenQueryParam = tab.addAccessTokenInQueryParam ? {accessToken: SystemUtils.getAccessToken()} : {}
  return (
    <iframe
      src={queryString.stringifyUrl({url: tab.url, query: {...iframeQueryParam, ...accessTokenQueryParam}})}
      width="100%"
      height="100%"
      frameBorder="0"
    />
  )
}

export const DynamicTab = memo(function DynamicComponent<CP extends { basepath?: string }>({
  tab,
  componentProps,
}: { tab: DynamicTabData, componentProps: CP }): JSX.Element {
  switch (tab.type) {
    case "Remote":
      return <RemoteModuleTab url={tab.url} componentProps={componentProps}/>
    case "IFrame":
      return <IframeTab tab={tab}/>
  }
})
