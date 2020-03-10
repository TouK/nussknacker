import React from "react"
import {useDispatch} from "react-redux"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {layout} from "../../../../../actions/nk/ui/layout"
import ToolbarButton from "../../../ToolbarButton"
import {useTranslation} from "react-i18next"
import {useGraph} from "../../../../graph/GraphContext"

function LayoutButton() {
  const dispatch = useDispatch()
  const {t} = useTranslation()
  const graphGetter = useGraph()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-layout.button", "layout")}
      icon={InlinedSvgs.buttonLayout}
      onClick={() => dispatch(layout(() => graphGetter().directedLayout()))}
    />
  )
}

export default LayoutButton
