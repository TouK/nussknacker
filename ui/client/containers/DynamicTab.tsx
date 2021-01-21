import * as queryString from "query-string"
import React, {memo} from "react"
import ErrorBoundary from "react-error-boundary"
import {ExternalModule, splitUrl, useExternalLib} from "./ExternalLib"
import {ModuleString, ModuleUrl} from "./ExternalLib/types"

type DynamicTabData = {
  title: string,
  id: string,
  url: string,
}

const RemoteTabComponent = ({scope}: {scope: ModuleString}) => {
  const {module: {default: Component}} = useExternalLib(scope)
  return <Component/>
}

const RemoteModuleTab = (props: {url: ModuleUrl}) => {
  const [url, scope] = splitUrl(props.url)
  return (
    <ExternalModule url={url}>
      <ErrorBoundary>
        <RemoteTabComponent scope={scope}/>
      </ErrorBoundary>
    </ExternalModule>
  )
}

const IframeTab = ({tab}: {tab: DynamicTabData}) => (
  <iframe
    src={queryString.stringifyUrl({url: tab?.url, query: {iframe: true}})}
    width="100%"
    height={window.innerHeight}
    frameBorder="0"
  />
)

export const DynamicTab = memo(function DynamicComponent({tab}: {tab: DynamicTabData}): JSX.Element {
  return tab?.url.match(/.js$/) ?
    <RemoteModuleTab url={tab.url}/> :
    <IframeTab tab={tab}/>
})
