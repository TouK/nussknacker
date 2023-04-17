/* eslint-disable i18next/no-literal-string */
import React from "react"
import Inspector from "react-inspector"

export function Debug({data}: {data: any}): JSX.Element {
  return (
    <div style={{zoom: 2}}>
      <Inspector expandLevel={1} theme="chromeDark" data={data}/>
    </div>
  )
}
