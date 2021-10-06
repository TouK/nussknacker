import * as queryString from "query-string"
import React, {useEffect, useState} from "react"
import {useSelector} from "react-redux"
import {useParams} from "react-router"
import HttpService from "../http/HttpService"
import {getMetricsSettings} from "../reducers/selectors/settings"
import {Page} from "./Page"

export const Metrics = (): JSX.Element => {
  const settings = useSelector(getMetricsSettings)

  if (!settings.url) {
    return (<div/>)
  }

  return <MetricsComponent settings={settings}/>
}

function MetricsComponent({settings}) {

  const [processingType, setProcessingType] = useState<string>(null)
  const {processId} = useParams<Record<"processId", string>>()

  useEffect(() => {
    if (processId) {
      HttpService.fetchProcessDetails(processId).then(response => {
        setProcessingType(response.data.processingType)
      })
    } else {
      setProcessingType("")
    }
  }, [processId])

  //TODO: this is still a bit grafana specific...
  const scenarioTypeToDashboard = settings.scenarioTypeToDashboard
  const dashboard = scenarioTypeToDashboard && scenarioTypeToDashboard[processingType] || settings.defaultDashboard

  const processName = processId || "All"

  const finalIframeUrl = queryString.stringifyUrl({
    url: settings.url.replace("$dashboard", dashboard).replace("$process", processName),
    query: {
      iframe: "true",
    },
  })

  return (
    <Page>
      <iframe
        src={finalIframeUrl}
        width="100%"
        height={window.innerHeight}
        frameBorder="0"
      />
    </Page>
  )
}

const basePath = `/metrics`
const path = `${basePath}/:processId?`

Metrics.basePath = basePath
Metrics.path = path
Metrics.header = "Metrics"

export default Metrics

export const pathForProcess = (processId) => `${basePath}/${processId}`
