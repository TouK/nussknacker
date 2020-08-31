import React, {useCallback} from "react"
import {Glyphicon} from "react-bootstrap"
import {GlyphiconProps} from "react-bootstrap/lib/Glyphicon"
import styles from "../../stylesheets/processes.styl"

const ENTER_KEY = "Enter"
export default function TableRowIcon(props: Pick<GlyphiconProps, "glyph" | "title" | "onClick">) {
  const {glyph, title, onClick} = props
  const handleKeyPress = useCallback(
    (event) => {
      if (event.key === ENTER_KEY) {
        onClick(null)
      }
    },
    [onClick],
  )

  return (
    <Glyphicon
      glyph={glyph}
      title={title}
      onClick={onClick}
      className={styles.processesTableRowIcon}
      tabIndex={0}
      onKeyPress={handleKeyPress}
    />
  )
}
