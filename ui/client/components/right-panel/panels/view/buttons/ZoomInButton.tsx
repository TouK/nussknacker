import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import ToolbarButton from "../../../ToolbarButton"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {zoomIn} from "../../../../../actions/nk/zoom"
import React from "react"
import {useGraph} from "../../../../graph/GraphContext"

export function ZoomInButton() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const graphGetter = useGraph()

  return (
    <ToolbarButton
      name={t("panels.actions.view-zoomIn.label", "zoom-in")}
      icon={InlinedSvgs.zoomIn}
      disabled={!graphGetter()}
      onClick={() => dispatch(zoomIn(graphGetter()))}
    />
  )
}
