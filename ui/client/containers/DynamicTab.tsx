import * as queryString from "query-string"
import React, {memo} from "react"
import {RemoteComponent} from "./RemoteComponent"

type DynamicTabData = {
  title: string,
  id: string,
  url: string,
}

export const DynamicTab = memo(function DynamicComponent({tab}: {tab: DynamicTabData}): JSX.Element {
  const [url, scope] = tab?.url.split(/@(?=http)/).reverse() || []
  return scope && url.match(/.js$/) ?
    (
      <RemoteComponent url={{url, scope}.url} scope={{url, scope}.scope} module={"."}/>
    ) :
    (
      <iframe
        src={queryString.stringifyUrl({url, query: {iframe: true}})}
        width="100%"
        height={window.innerHeight}
        frameBorder="0"
      />
    )
})
