import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {resetToolbars} from "../../../../actions/nk/toolbars"
import React from "react"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/resetgui.svg"
import {ToolbarButtonProps} from "../../types"

export function ResetViewButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const {disabled} = props

  return (
    <ToolbarButton
      name={t("panels.actions.view-reset.label", "reset")}
      icon={<Icon/>}
      disabled={disabled}
      onClick={() => dispatch(resetToolbars())}
    />
  )
}
