import {useDispatch} from "react-redux"
import {resetToolbars} from "../../actions/nk/toolbars"
import React from "react"
import {ToolbarButton} from "./ToolbarButton"
import {useTranslation} from "react-i18next"

export function Reset() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  return (
    <ToolbarButton
      name={t("panels.actions.view-reset.label", "reset")}
      icon="zoomin.svg"
      onClick={() => dispatch(resetToolbars())}
    />
  )
}
