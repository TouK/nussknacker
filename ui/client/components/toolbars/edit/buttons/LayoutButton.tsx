import React from "react"
import {useThunkDispatch} from "../../../../store/configureStore"
import {layout} from "../../../../actions/nk/ui/layout"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {useTranslation} from "react-i18next"
import {useGraph} from "../../../graph/GraphContext"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/layout.svg"

function LayoutButton() {
  const dispatch = useThunkDispatch()
  const {t} = useTranslation()
  const graphGetter = useGraph()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-layout.button", "layout")}
      icon={<Icon/>}
      onClick={() => dispatch(layout(() => graphGetter().forceLayout()))}
    />
  )
}

export default LayoutButton
