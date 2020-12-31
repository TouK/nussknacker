import React from "react"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton";
import {ReactComponent as DefaultIcon} from "../../../../assets/img/toolbarButtons/custom_action.svg";
import {loadProcessState} from "../../../../actions/nk";
import HttpService from "../../../../http/HttpService";
import {useDispatch} from "react-redux";
import {CustomAction, StatusType} from "../../../Process/types";
import {useTranslation} from "react-i18next";

type Props = {
  action: CustomAction,
  processId: string,
  processStatus: StatusType
}

export default function CustomActionButton(props: Props) {

  const { action, processId, processStatus } = props

  const dispatch = useDispatch()
  const {t} = useTranslation()

  const icon = action.icon
      ? <img alt={`custom-action-${action.name}`} src={action.icon} />
      : <DefaultIcon/>

  const statusName = processStatus.name
  const isDisabled = !action.allowedProcessStates.map(s => s.name).includes(statusName)

  const toolTip = isDisabled
      ? t("panels.actions.custom-action.tooltips.disabled", "Disabled for {{statusName}} status.", {statusName})
      : null

  const onClick = () => {
    confirm(`Do you want to run ${action.name}`)
      && dispatch(HttpService
          .customAction(processId, action.name)
          .finally(() => dispatch(loadProcessState(processId))))
  }

  return <ToolbarButton
    name={action.name}
    title={toolTip}
    disabled={isDisabled}
    icon={icon}
    onClick={onClick}
  />
}