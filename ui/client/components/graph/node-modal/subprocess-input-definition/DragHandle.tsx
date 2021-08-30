import {css, cx} from "emotion"
import React, {useCallback, useEffect, useState} from "react"
import {ReactComponent as Handlebars} from "../../../../assets/img/handlebars.svg"
import {useNkTheme} from "../../../../containers/theme"

const grabbing = css({"*": {cursor: "grabbing !important"}})

export function DragHandle({active}: {active?: boolean}): JSX.Element {
  const [isActive, setActive] = useState(false)
  const {theme} = useNkTheme()
  useEffect(() => {setActive(active)}, [active])
  useEffect(() => {document.body.classList.toggle(grabbing, isActive)}, [isActive])

  const onMouseDown = useCallback(() => setActive(true), [])
  const onMouseUp = useCallback(() => setActive(false), [])

  return (
    <Handlebars
      onMouseDown={onMouseDown}
      onMouseUp={onMouseUp}
      className={cx("handle-bars", css({
        g: {fill: isActive ? theme.colors.accent : theme.colors.primary},
      }))}
    />
  )
}
