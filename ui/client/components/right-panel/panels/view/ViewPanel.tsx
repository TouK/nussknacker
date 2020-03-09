import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import BussinesViewSwitch from "./BussinesViewSwitch"
import {zoomIn, zoomOut} from "../../../../actions/nk/zoom"
import {useDispatch} from "react-redux"
import {Graph} from "../../ToolsLayer"
import {ToolbarButton} from "../../ToolbarButton"
import {resetToolbars} from "../../../../actions/nk/toolbars"
import {ToolbarButtons} from "../../../Process/ToolbarButtons"
import * as InlinedSvgs from "../../../../assets/icons/InlinedSvgs"

interface Props {
  graphGetter: () => Graph,
}

function ViewPanel({graphGetter}: Props) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const onZoomIn = () => dispatch(zoomIn(graphGetter()))
  const onZoomOut = () => dispatch(zoomOut(graphGetter()))

  return (
    <CollapsibleToolbar id="VIEW-PANEL" title={t("panels.view.title", "view")}>
      <ToolbarButtons>
        <BussinesViewSwitch/>
        <ToolbarButton
          name={t("panels.actions.view-reset.label", "reset")}
          icon={InlinedSvgs.resetGui}
          onClick={() => dispatch(resetToolbars())}
        />
        <ToolbarButton
          name={t("panels.actions.view-zoomIn.label", "zoom-in")}
          icon={InlinedSvgs.zoomIn}
          onClick={onZoomIn}
        />
        <ToolbarButton
          name={t("panels.actions.view-zoomOut.label", "zoom-out")}
          icon={InlinedSvgs.zoomOut}
          onClick={onZoomOut}
        />
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(ViewPanel)

