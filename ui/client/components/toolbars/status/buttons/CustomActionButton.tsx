import React from "react"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ReactComponent as DefaultIcon} from "../../../../assets/img/toolbarButtons/custom_action.svg"
import {toggleCustomAction} from "../../../../actions/nk"
import {useDispatch} from "react-redux"
import {StatusType} from "../../../Process/types"
import {useTranslation} from "react-i18next"
import {CustomAction} from "../../../../types"
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

  const toolTip = available ?
    t("panels.actions.custom-action.tooltips.disabled", "Disabled for {{statusName}} status.", {statusName}) :
    null

  return (
    <ToolbarButton
      name={action.name}
      title={toolTip}
      disabled={!available}
      icon={icon}
      onClick={() => dispatch(toggleCustomAction(action))}
    />
  )
}
