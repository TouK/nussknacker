import React from "react"
import {connect} from "react-redux"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {layout} from "../../../../../actions/nk/ui/layout"
import {ToolbarButton} from "../../../ToolbarButton"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../../ToolsLayer"

type OwnPropsPick = Pick<PassedProps, "graphLayoutFunction">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function LayoutButton(props: Props) {
  const {graphLayoutFunction, layout} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-layout.button", "layout")}
      icon={InlinedSvgs.buttonLayout}
      onClick={() => layout(graphLayoutFunction)}
    />
  )
}

const mapDispatch = {
  layout,
}

type StateProps = typeof mapDispatch

export default connect(null, mapDispatch)(LayoutButton)
