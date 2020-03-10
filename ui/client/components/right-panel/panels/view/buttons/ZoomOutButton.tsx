import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import {ToolbarButton} from "../../../ToolbarButton"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {zoomOut} from "../../../../../actions/nk/zoom"
import React from "react"
import {useGraph} from "../../../../graph/GraphContext"

export function ZoomOutButton() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const graphGetter = useGraph()

  return (
    <ToolbarButton
      name={t("panels.actions.view-zoomOut.label", "zoom-out")}
      icon={InlinedSvgs.zoomOut}
      disabled={!graphGetter()}
      onClick={() => dispatch(zoomOut(graphGetter()))}
    />
  )
}
