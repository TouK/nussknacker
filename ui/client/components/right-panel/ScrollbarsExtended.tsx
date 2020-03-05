import React, {PropsWithChildren, useRef, useState, useEffect} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {useDebouncedCallback} from "use-debounce"
import cn from "classnames"
import styles from "./ScrollbarsExtended.styl"

export function ScrollbarsExtended({children}: PropsWithChildren<{}>) {
  const scrollbars = useRef<Scrollbars>()
  const [isScrollPossible, setScrollPossible] = useState<boolean>()

  const [onUpdate] = useDebouncedCallback(() => {
    const {scrollHeight = 0, clientHeight = 0} = scrollbars.current?.getValues() || {}
    setScrollPossible(scrollHeight - clientHeight > 0)
  }, 100)

  useEffect(() => {
    window.addEventListener("mousemove", onUpdate)
    return () => window.removeEventListener("mousemove", onUpdate)
  }, [onUpdate])

  return (
    <Scrollbars
      renderThumbVertical={props => <div {...props} className="thumbVertical"/>}
      hideTracksWhenNotNeeded={true}
      ref={scrollbars}
      onUpdate={onUpdate}
      className={cn(isScrollPossible ? styles.enabled : styles.disabled)}
    >
      {children}
    </Scrollbars>
  )
}
