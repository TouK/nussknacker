import React, {useCallback, useState} from "react"
import {hot} from "react-hot-loader"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import InlinedSvgs from "../assets/icons/InlinedSvgs"
import {useInterval} from "../containers/Interval"
import HttpService, {HealthCheckResponse} from "../http/HttpService"
import {getHealthcheckIntervalTime} from "../reducers/selectors/settings"
import styles from "./healthCheck.styl"

const STATE_OK = "ok"

function HealthCheck(): JSX.Element {
  const {t} = useTranslation()
  const [{error, state}, setState] = useState<HealthCheckResponse>({state: STATE_OK})

  const updateState = useCallback(async () => {
    setState(await HttpService.fetchHealthCheckProcessDeployment())
  }, [])

  const refreshTime = useSelector(getHealthcheckIntervalTime)
  useInterval(updateState, refreshTime)

  if (state === STATE_OK) {return null}
  return (
    <div className={styles.healthCheck}>
      <div className={styles.icon} title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
      <span className={styles.errorText}>{error || t("healthCheck.unknownState", "State unknown")}</span>
    </div>
  )
}

export default hot(module)(HealthCheck)
