import React, {useEffect, useMemo, useState} from "react"
import {useSelector} from "react-redux"
import {useParams} from "react-router-dom"
import HttpService from "../http/HttpService"
import {getMetricsSettings} from "../reducers/selectors/settings"
import {CustomTabWrapper, useTabData} from "./CustomTabPage"
import {ProcessId} from "../types"

function useExtendedMetricsTab(processId?: ProcessId) {
  const [processingType, setProcessingType] = useState("")
  useEffect(() => {
    if (processId) {
      HttpService.fetchProcessDetails(processId).then(({data}) => {
        setProcessingType(data.processingType)
      })
    } else {
      setProcessingType("")
    }
  }, [processId])

  const settings = useSelector(getMetricsSettings)
  const tab = useTabData("metrics")
  return useMemo(() => {
    const dashboard = settings.scenarioTypeToDashboard?.[processingType] || settings.defaultDashboard
    const scenarioName = processId || "All"
    return {
      ...tab,
      url: settings.url?.replace("$dashboard", dashboard).replace("$scenarioName", scenarioName) || tab.url,
    }
  }, [processId, processingType, settings, tab])
}

function Metrics(): JSX.Element {
  const {processId} = useParams<{ processId: string }>()
  const tab = useExtendedMetricsTab(processId)
  return <CustomTabWrapper tab={tab}/>
}

export default Metrics
