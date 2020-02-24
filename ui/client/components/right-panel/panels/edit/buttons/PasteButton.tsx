/* eslint-disable i18next/no-literal-string */
import React from "react"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import {pasteSelection} from "../../../../../actions/nk/selection"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../../UserRightPanel"

type OwnPropsPick = Pick<PassedProps,
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function PasteButton(props: Props) {
  const {selectionActions, pasteSelection} = props
  const {t} = useTranslation()

  const {canPaste, paste} = selectionActions
  return (
    <ButtonWithIcon
      name={t("panels.actions.edit-paste.button", "paste")}
      icon={"paste.svg"}
      disabled={!canPaste}
      onClick={event => pasteSelection(
        () => paste(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      )}
    />
  )
}

const mapDispatch = {
  pasteSelection,
}

type StateProps = typeof mapDispatch

export default connect(null, mapDispatch)(PasteButton)
