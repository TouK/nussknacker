import React from "react"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ReactComponent as DefaultIcon} from "../../../../assets/img/toolbarButtons/custom_action.svg"
import {toggleCustomAction} from "../../../../actions/nk"
import {useDispatch} from "react-redux"
import {StatusType} from "../../../Process/types"
import {useTranslation} from "react-i18next"
import {CustomAction} from "../../../../types"

type Props = {
  action: CustomAction,
  processId: string,
  processStatus: StatusType | null,
}

export default function CustomActionButton(props: Props) {

  const {action, processId, processStatus} = props

  const dispatch = useDispatch()
  const {t} = useTranslation()

  const icon = action.icon ?
    <img alt={`custom-action-${action.name}`} src={action.icon}/> :
    <DefaultIcon/>

  const statusName = processStatus?.name
  const isDisabled = !action.allowedStateStatusNames.includes(statusName)

  const toolTip = isDisabled ?
    t("panels.actions.custom-action.tooltips.disabled", "Disabled for {{statusName}} status.", {statusName}) :
    null

  return (
    <ToolbarButton
      name={action.name}
      title={toolTip}
      disabled={isDisabled}
      icon={icon}
      onClick={() => dispatch(toggleCustomAction(action))}
    />
  )
}
