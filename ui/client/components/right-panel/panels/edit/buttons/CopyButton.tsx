import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import {copySelection} from "../../../../../actions/nk/selection"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {useTranslation} from "react-i18next"

type OwnPropsPick = Pick<PanelOwnProps,
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function CopyButton(props: Props) {
  const {selectionActions, copySelection} = props
  const {copy, canCopy} = selectionActions
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.edit.actions.copy.button", "copy")}
      icon={"copy.svg"}
      disabled={!canCopy}
      onClick={event => copySelection(
        () => copy(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      )}
    />
  )
}

const mapDispatch = {
  copySelection,
}

type StateProps = typeof mapDispatch

export default connect(null, mapDispatch)(CopyButton)
