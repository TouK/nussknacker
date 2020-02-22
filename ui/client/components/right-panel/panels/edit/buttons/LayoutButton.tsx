/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {layout} from "../../../../../actions/nk/ui/layout"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type OwnPropsPick = Pick<PanelOwnProps, "graphLayoutFunction">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function LayoutButton(props: Props) {
  const {graphLayoutFunction, layout} = props
  return (
    <ButtonWithIcon
      name={"layout"}
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
