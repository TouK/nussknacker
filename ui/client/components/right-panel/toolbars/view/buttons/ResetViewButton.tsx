import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import ToolbarButton from "../../../toolbarsComponents/ToolbarButton"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {resetToolbars} from "../../../../../actions/nk/toolbars"
import React from "react"

export function ResetViewButton() {
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <ToolbarButton
      name={t("panels.actions.view-reset.label", "reset")}
      icon={InlinedSvgs.resetGui}
      onClick={() => dispatch(resetToolbars())}
    />
  )
}
