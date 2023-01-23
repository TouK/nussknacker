import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import {ReactComponent as DefaultIcon} from "../../../../assets/img/toolbarButtons/custom_action.svg"
import {CustomAction} from "../../../../types"
import {useWindows} from "../../../../windowManager"
import {WindowKind} from "../../../../windowManager/WindowKind"
import {StatusType} from "../../../Process/types"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"

type CustomActionProps = {
  action: CustomAction,
  processId: string,
  processStatus: StatusType | null,
} & ToolbarButtonProps

export default function CustomActionButton(props: CustomActionProps) {

  const {action, processStatus, disabled} = props

  const dispatch = useDispatch()
  const {t} = useTranslation()

  const icon = action.icon ?
    <img alt={`custom-action-${action.name}`} src={action.icon}/> :
    <DefaultIcon/>

  const statusName = processStatus?.name
  const available = !disabled && action.allowedStateStatusNames.includes(statusName)

  const toolTip = available ? null :
    t("panels.actions.custom-action.tooltips.disabled", "Disabled for {{statusName}} status.", {statusName})

  const {open} = useWindows()
  return (
    <ToolbarButton
      name={action.name}
      title={toolTip}
      disabled={!available}
      icon={icon}
      onClick={() => open<CustomAction>({
        title: action.name,
        kind: WindowKind.customAction,
        meta: action,
      })}
    />
  )
}
