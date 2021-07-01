import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {zoomIn} from "../../../../actions/nk/zoom"
import React from "react"
import {useGraph} from "../../../graph/GraphContext"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/zoom-in.svg"
import {ToolbarButtonProps} from "../../types"

export function ZoomInButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const graphGetter = useGraph()
  const {disabled} = props
  const available = !disabled && graphGetter()

  return (
    <ToolbarButton
      name={t("panels.actions.view-zoomIn.label", "zoom-in")}
      icon={<Icon/>}
      disabled={!available}
      onClick={() => dispatch(zoomIn(graphGetter()))}
    />
  )
}
