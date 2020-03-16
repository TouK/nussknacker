/* eslint-disable i18next/no-literal-string */
import React, {PropsWithChildren, useRef, useState, useEffect} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {useDebouncedCallback} from "use-debounce"
import cn from "classnames"
import styles from "./ScrollbarsExtended.styl"

const OBSERVED_EVENTS = ["mouseover", "click", "keyup"]

export function ScrollbarsExtended({children, onScrollToggle}: PropsWithChildren<{ onScrollToggle?: (isEnabled: boolean) => void }>) {
  const scrollbars = useRef<Scrollbars>()
  const [isScrollPossible, setScrollPossible] = useState<boolean>()

  const [onUpdate] = useDebouncedCallback(() => {
    const {scrollHeight = 0, clientHeight = 0} = scrollbars.current?.getValues() || {}
    setScrollPossible(scrollHeight > clientHeight)
  }, 50)

  useEffect(() => {
    OBSERVED_EVENTS.forEach(eventName => window.addEventListener(eventName, onUpdate))
    return () => OBSERVED_EVENTS.forEach(eventName => window.removeEventListener(eventName, onUpdate))
  }, [onUpdate])

  useEffect(() => {
    onScrollToggle?.(isScrollPossible)
  }, [isScrollPossible])

  return (
    <Scrollbars
      renderTrackVertical={props => <div {...props} className={cn(styles.track, styles.vertical)}/>}
      renderThumbVertical={props => <div {...props} className={cn(styles.thumb, styles.vertical)}/>}
      hideTracksWhenNotNeeded={false}
      autoHide={true}
      ref={scrollbars}
      onUpdate={onUpdate}
      className={cn(isScrollPossible ? styles.enabled : styles.disabled)}
    >
      <div className={cn(styles.wrapper, isScrollPossible ? styles.enabled : styles.disabled)}>
        {children}
      </div>
    </Scrollbars>
  )
}
