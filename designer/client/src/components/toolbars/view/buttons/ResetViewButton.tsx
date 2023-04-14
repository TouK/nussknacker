import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {resetToolbars} from "../../../../actions/nk/toolbars"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/resetgui.svg"
import {getToolbarsConfigId} from "../../../../reducers/selectors/toolbars"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"

export function ResetViewButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const configId = useSelector(getToolbarsConfigId)
  const {disabled} = props

  return (
    <ToolbarButton
      name={t("panels.actions.view-reset.label", "reset")}
      icon={<Icon/>}
      disabled={disabled}
      onClick={() => dispatch(resetToolbars(configId))}
    />
  )
}
