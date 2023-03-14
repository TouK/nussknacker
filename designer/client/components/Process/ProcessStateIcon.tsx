import React, {PropsWithChildren, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {ProcessStateType, ProcessType} from "./types"
import {Popover} from "react-bootstrap"
import {OverlayTrigger} from "react-bootstrap/lib"
import ProcessStateUtils from "./ProcessStateUtils"
import {css} from "@emotion/css"
import UrlIcon from "../UrlIcon"
import {useTheme} from "@emotion/react"
import {ProcessId} from "../../types"

interface Props {
  processState?: ProcessStateType,
  isStateLoaded?: boolean,
  process: ProcessType,
}

function Errors({state}: { state: ProcessStateType }) {
  const {t} = useTranslation()

  if (state.errors?.length < 1) {
    return null
  }

  return (
    <div>
      <span>{t("stateIcon.errors", "Errors:")}</span>
      <ul>
        {state.errors.map((error, key) => <li key={key}>{error}</li>)}
      </ul>
    </div>
  )
}

function StateIconPopover({processName, processState, tooltip, children}: PropsWithChildren<{
  processState: ProcessStateType,
  processName: ProcessId,
  tooltip: string,
}>) {
  const theme = useTheme()
  const imagePopover = useMemo(() => {
    const className = css({
      marginTop: "5px",
      "&, h3": {backgroundColor: theme.colors?.primaryBackground},
    })
    return (
      <Popover id="state-icon-popover" className={className} title={processName}>
        <strong>{tooltip}</strong>
        <Errors state={processState}/>
      </Popover>
    )
  }, [processName, processState, theme.colors?.primaryBackground, tooltip])

  return (
    <OverlayTrigger trigger="click" placement="left" overlay={imagePopover}>
      {children}
    </OverlayTrigger>
  )
}

function ProcessStateIcon({process, processState, isStateLoaded}: Props) {
  const icon = ProcessStateUtils.getStatusIcon(process, processState, isStateLoaded)
  const tooltip = ProcessStateUtils.getStatusTooltip(process, processState, isStateLoaded)

  return (
    <StateIconPopover
      processName={process.name}
      processState={isStateLoaded ? processState : process.state}
      tooltip={tooltip}
    >
      <UrlIcon src={icon} title={tooltip}/>
    </StateIconPopover>
  )
}

export default ProcessStateIcon

