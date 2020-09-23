import {css} from "emotion"
import React, {useCallback, useState} from "react"
import {hot} from "react-hot-loader"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../assets/icons/tipsWarning.svg"
import {useInterval} from "../containers/Interval"
import {ProcessLink} from "../containers/processLink"
import {NkTheme, useNkTheme} from "../containers/theme"
import HttpService, {HealthCheckResponse} from "../http/HttpService"
import {getHealthcheckIntervalTime} from "../reducers/selectors/settings"

const STATE_OK = "ok"

const getIconSize = (theme: NkTheme) => theme.spacing.controlHeight / 2
const getBackground = (theme: NkTheme) => theme.colors.primaryBackground

const getTextStyles = (theme: NkTheme) => (expanded: boolean) => {
  const iconSize = getIconSize(theme)
  return css({
    display: "flex",
    flexWrap: "wrap",
    alignItems: "center",
    flex: 1,
    paddingTop: iconSize / 2,
    paddingBottom: iconSize / 2,
    minHeight: iconSize,
    overflow: "hidden",
    maxHeight: expanded ? "50vh" : iconSize * 2 + 2,
    whiteSpace: !expanded ? "nowrap" : "normal",
  })
}

const getButtonStyles = (theme: NkTheme) => {
  const iconSize = getIconSize(theme)
  return css({
    flexShrink: 0,
    height: iconSize * 2,
    background: "none",
    border: 0,
    fontWeight: "bold",
    textTransform: "uppercase",
  })
}

const getIconStyles = (theme: NkTheme) => {
  const iconSize = getIconSize(theme)
  const color = getBackground(theme)
  return css({
    margin: iconSize / 2,
    flexShrink: 0,
    path: {
      fill: color,
    },
    width: iconSize,
    height: iconSize,
  })
}

const getContainerStyles = (theme: NkTheme) => {
  const iconSize = getIconSize(theme)
  const color = getBackground(theme)
  const backgroundColor = theme.colors.warning
  return css({
    display: "flex",
    alignItems: "flex-start",
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
}

const Container = ({children}) => {
  const {t} = useTranslation()
  const title = t("healthCheck.warning", "Warning")
  const {theme} = useNkTheme()
  const containerStyles = getContainerStyles(theme)
  return (
    <div className={containerStyles} title={title}>{children}</div>
  )
}

const Ico = () => {
  const {theme} = useNkTheme()
  const iconStyles = getIconStyles(theme)
  return (
    <Icon className={iconStyles}/>
  )
}

const Button = ({expanded, setExpanded}) => {
  const {theme} = useNkTheme()
  const buttonStyles = getButtonStyles(theme)
  return (
    <button className={buttonStyles} onClick={() => setExpanded(!expanded)}>toggle</button>
  )
}

const Content = ({expanded, data}: {expanded: boolean, data: Omit<HealthCheckResponse, "state">}) => {
  const {theme} = useNkTheme()
  const textStyles = getTextStyles(theme)
  const {t} = useTranslation()

  return (
    <div className={textStyles(expanded)}>
      <span className={css({overflow: "hidden", textOverflow: "ellipsis"})}>
        {data.error || t("healthCheck.unknownState", "State unknown")}
        {": "}
        {[
          ...data.processes, ...data.processes, ...data.processes, ...data.processes, ...data.processes, ...data.processes,
          ...data.processes, ...data.processes,
        ]?.map((name, index) => (
          <React.Fragment key={name}>
            {index !== 0 && ", "}
            <ProcessItem name={name}/>
          </React.Fragment>
        ))}
      </span>
    </div>
  )
}

const ProcessItem = ({name}: {name: string}) => {
  const {theme} = useNkTheme()
  return (
    <ProcessLink
      processId={name}
      className={css({
        fontWeight: "bold",
        color: theme.colors.neutral0,
      })}
    >{name}</ProcessLink>
  )
}

function HealthCheck(): JSX.Element {
  const [expanded, setExpanded] = useState<boolean>(false)
  const [{state, ...data}, setState] = useState<HealthCheckResponse>({state: STATE_OK})
  const updateState = useCallback(async () => {
    setState(await HttpService.fetchHealthCheckProcessDeployment())
  }, [])
  const refreshTime = useSelector(getHealthcheckIntervalTime)
  useInterval(updateState, {refreshTime, ignoreFirst: false})

  if (state === STATE_OK) return null
  return (
    <Container>
      <Ico/>
      <Content expanded={expanded} data={data}/>
      <Button expanded={expanded} setExpanded={setExpanded}/>
    </Container>
  )
}

export default hot(module)(HealthCheck)
