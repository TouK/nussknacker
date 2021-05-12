import {css} from "emotion"
import React, {PropsWithChildren, useCallback, useEffect, useRef, useState} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {Transition} from "react-transition-group"
import {ReactComponent as TipsWarningIcon} from "../assets/icons/tipsWarning.svg"
import {ReactComponent as CollapseIcon} from "../assets/img/arrows/panel-hide-arrow.svg"
import {useSizeWithRef} from "../containers/hooks/useSize"
import {useInterval} from "../containers/Interval"
import {ProcessLink} from "../containers/processLink"
import {NkTheme, useNkTheme} from "../containers/theme"
import HttpService, {HealthCheckResponse, HealthState} from "../http/HttpService"
import {getHealthcheckIntervalTime} from "../reducers/selectors/settings"

const getIconSize = (theme: NkTheme) => theme.spacing.controlHeight / 2
const getBackground = (theme: NkTheme) => theme.colors.primaryBackground

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

function Content({data}: {data: Omit<HealthCheckResponse, "state">}) {
  const {t} = useTranslation()
  return (
    <span className={css({fontWeight: 600})}>
      {data.error || t("healthCheck.unknownState", "State unknown")}
      {": "}
      {data.processes?.map((name, index) => (
        <React.Fragment key={name + index}>
          {index !== 0 && ", "}
          <ProcessItem name={name}/>
        </React.Fragment>
      ))}
    </span>
  )
}

function Collipsable({iconSize, children}: PropsWithChildren<{iconSize: number}>) {
  const [expanded, setExpanded] = useState<boolean>(false)
  useEffect(
    () => {
      if (expanded) {
        const timeout = setTimeout(() => setExpanded(e => !e), 5000)
        return () => clearTimeout(timeout)
      }
    },
    [expanded],
  )
  const ref = useRef<HTMLDivElement>()
  const {observe} = useSizeWithRef<HTMLDivElement>(ref)
  const isOverflow = expanded || ref.current?.offsetWidth < ref.current?.scrollWidth
  const {t} = useTranslation()
  const {theme} = useNkTheme()

  return (
    <Transition in={expanded} timeout={500}>
      {state => (
        <>
          <div
            className={css({
              display: "flex",
              flexWrap: "wrap",
              alignItems: "center",
              flex: 1,
              overflow: "hidden",
              minHeight: iconSize * 2,
              maxHeight: state === "entering" || state === "entered" ? "60vh" : iconSize * 2,
              whiteSpace: state === "exited" ? "nowrap" : "normal",
              transition: "all .25s ease-in-out",
            })}
          >
            <div
              ref={observe}
              className={css({
                overflow: "hidden",
                textOverflow: "ellipsis",
                paddingTop: iconSize / 2,
                paddingBottom: iconSize / 2,
                paddingRight: iconSize,
              })}
            >
              {children}
            </div>
          </div>
          {(isOverflow || state !== "exited") && (
            <button
              className={css({
                display: "flex",
                alignItems: "center",
                flexShrink: 0,
                background: "none",
                border: 0,
                fontWeight: "bold",
                textTransform: "uppercase",
                height: iconSize * 2,
              })}
              onClick={() => setExpanded(expanded => !expanded)}
            >
              {expanded ? t("healthCheck.hide", "hide") : t("healthCheck.show", "show all")}
              <CollapseIcon className={css({
                height: ".7em",
                marginLeft: ".5em",
                path: {fill: theme.colors.neutral0},
                transition: "transform .2s",
                transform: expanded ? "rotate(90deg)" : null,
                transformOrigin: "50% 50%",
              })}
              />
            </button>
          )}
        </>
      )}
    </Transition>
  )
}

function HealthCheck(): JSX.Element {
  const [{state, ...data}, setState] = useState<HealthCheckResponse>({state: HealthState.ok})
  const updateState = useCallback(async () => {
    setState(await HttpService.fetchHealthCheckProcessDeployment())
  }, [])
  const refreshTime = useSelector(getHealthcheckIntervalTime)
  useInterval(updateState, {refreshTime, ignoreFirst: false})

  const {t} = useTranslation()
  const {theme} = useNkTheme()
  const iconSize = getIconSize(theme)
  const background = getBackground(theme)
  if (state === HealthState.ok) return null
  return (
    <div
      className={css({
        display: "flex",
        alignItems: "flex-start",
        borderStyle: "solid",
        borderWidth: 1,
        color: background,
        backgroundColor: theme.colors.error,
        borderRadius: theme.borderRadius,
        marginTop: iconSize,
        marginBottom: iconSize * 2,
        fontSize: theme.fontSize,
      })}
      title={t("healthCheck.warning", "Warning")}
    >
      <TipsWarningIcon
        className={css({
          margin: iconSize / 2,
          flexShrink: 0,
          path: {
            fill: background,
          },
          width: iconSize,
          height: iconSize,
        })}
      />
      <Collipsable iconSize={iconSize}>
        <Content data={data}/>
      </Collipsable>
    </div>
  )
}

export default HealthCheck
