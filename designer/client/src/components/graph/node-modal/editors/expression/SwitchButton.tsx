import React from "react"
import {ButtonWithFocus} from "../../../../withFocus"
import styled from "@emotion/styled"
import {EditorType} from "./Editor"
import {useTheme} from "@emotion/react"
import {css} from "@emotion/css"

export const SwitchButton = styled(ButtonWithFocus)(({disabled, theme}) => ({
  width: 35,
  height: 35,
  padding: 5,
  backgroundColor: theme.colors.secondaryBackground,
  border: "none",
  opacity: disabled ? .5 : 1,
  filter: disabled ? "saturate(0)" : "non",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
}))

import {ReactComponent as CodeIcon} from "./icons/code.svg"
import {ReactComponent as TextIcon} from "./icons/code_off.svg"
import {ReactComponent as ListIcon} from "./icons/list.svg"
import {ReactComponent as ScheduleIcon} from "./icons/schedule.svg"
import {ReactComponent as DateIcon} from "./icons/date_range.svg"

function getTypeIcon(type: EditorType) {
  switch (type) {
    case EditorType.FIXED_VALUES_PARAMETER_EDITOR:
      return ListIcon
    case EditorType.DURATION_EDITOR:
    case EditorType.CRON_EDITOR:
    case EditorType.PERIOD_EDITOR:
    case EditorType.TIME:
      return ScheduleIcon
    case EditorType.DATE:
    case EditorType.DATE_TIME:
      return DateIcon
    case EditorType.SQL_PARAMETER_EDITOR:
      return CodeIcon
    default:
      return TextIcon
  }
}

export function SimpleEditorIcon({type}: { type: EditorType }) {
  const theme = useTheme()
  const Icon = getTypeIcon(type)
  return <Icon className={css({color: theme.colors.ok})}/>
}

export const RawEditorIcon = CodeIcon
