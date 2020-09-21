import {css} from "emotion"
import React, {useCallback, useState} from "react"
import {hot} from "react-hot-loader"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../assets/icons/tipsWarning.svg"
import {useInterval} from "../containers/Interval"
import {useNkTheme} from "../containers/theme"
import HttpService, {HealthCheckResponse} from "../http/HttpService"
import {getHealthcheckIntervalTime} from "../reducers/selectors/settings"

const STATE_OK = "ok"

function HealthCheck(): JSX.Element {
  const {theme} = useNkTheme()
  const {t} = useTranslation()
  const [{error, state}, setState] = useState<HealthCheckResponse>({state: STATE_OK})

  const updateState = useCallback(async () => {
    setState(await HttpService.fetchHealthCheckProcessDeployment())
  }, [])

  const refreshTime = useSelector(getHealthcheckIntervalTime)
  useInterval(updateState, {refreshTime, ignoreFirst: true})

  if (state === STATE_OK) {return null}

  const title = t("healthCheck.warning", "Warning")

  const iconSize = theme.spacing.controlHeight / 2
  const color = theme.colors.primaryBackground
  const backgroundColor = theme.colors.warning
  const containerStyles = css({
    display: "flex",
    alignItems: "center",
    borderStyle: "solid",
    borderWidth: 1,
    color,
    backgroundColor,
    borderRadius: theme.borderRadius,
    marginTop: iconSize,
    marginBottom: iconSize * 2,
    padding: theme.spacing.baseUnit / 2,
    fontSize: theme.fontSize,
  })

  const iconStyles = css({
    margin: iconSize / 2,
    path: {
      fill: color,
    },
    width: iconSize,
    height: iconSize,
  })

  return (
    <div className={containerStyles} title={title}>
      <Icon className={iconStyles}/>
      <span>{error || t("healthCheck.unknownState", "State unknown")}</span>
    </div>
  )
}

export default hot(module)(HealthCheck)
