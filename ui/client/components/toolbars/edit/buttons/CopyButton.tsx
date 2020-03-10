import React from "react"
import {connect} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {copySelection} from "../../../../actions/nk/selection"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {useTranslation} from "react-i18next"
import {SelectionActions} from "../EditPanel"

type OwnProps = {
  selectionActions: SelectionActions,
}

type Props = OwnProps & StateProps

function CopyButton(props: Props) {
  const {selectionActions, copySelection} = props
  const {copy, canCopy} = selectionActions
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-copy.button", "copy")}
      icon={"new/copy.svg"}
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
