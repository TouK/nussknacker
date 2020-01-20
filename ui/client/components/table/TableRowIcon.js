import React from "react"
import {Glyphicon} from "react-bootstrap"

export default function TableRowIcon(props) {

  const {glyph, title, onClick, onKeyPress} = props

  const enterKey = "Enter"

  const handleKeyPress = (event) => {
    if (event.key === enterKey) {
      onClick()
    }
  }

  return <Glyphicon glyph={glyph}
                    title={title}
                    onClick={onClick}
                    className={"processes-table-row-icon"}
                    tabIndex={0}
                    onKeyPress={handleKeyPress}
  />
}
