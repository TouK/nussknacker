/* eslint-disable i18next/no-literal-string */
import React, {PropsWithChildren, useState, useEffect} from "react"
import Scrollbars from "react-scrollbars-custom"
import cn from "classnames"
import styles from "./ScrollbarsExtended.styl"

const SCROLLBAR_WIDTH = 40 //some value bigger than real scrollbar width
const CLEAN_STYLE = null

export function ScrollbarsExtended({children, onScrollToggle}: PropsWithChildren<{ onScrollToggle?: (isEnabled: boolean) => void }>) {
  const [isScrollPossible, setScrollPossible] = useState<boolean>()

  useEffect(() => {
    onScrollToggle?.(isScrollPossible)
  }, [isScrollPossible])

  return (
    <Scrollbars
      noScrollX
      disableTracksWidthCompensation
      trackYProps={{
        className: cn(styles.track, styles.vertical),
        style: {
          background: CLEAN_STYLE,
          borderRadius: CLEAN_STYLE,
          left: CLEAN_STYLE,
          right: CLEAN_STYLE,
          width: CLEAN_STYLE,
          top: CLEAN_STYLE,
          height: CLEAN_STYLE,
        },
      }}
      thumbYProps={{
        className: cn(styles.thumb, styles.vertical),
        style: {
          background: CLEAN_STYLE,
          borderRadius: CLEAN_STYLE,
          cursor: CLEAN_STYLE,
        },
      }}
      contentProps={{
        className: cn(styles.content),
        style: {
          padding: CLEAN_STYLE,
        },
      }}
      scrollbarWidth={SCROLLBAR_WIDTH}
      scrollerProps={{
        style: {
          marginRight: -SCROLLBAR_WIDTH,
        },
      }}
      onUpdate={({scrollYPossible}) => setScrollPossible(scrollYPossible)}
      className={cn(isScrollPossible ? styles.enabled : styles.disabled)}
    >
      <div className={cn(styles.wrapper, isScrollPossible ? styles.enabled : styles.disabled)}>
        {children}
      </div>
    </Scrollbars>
  )
}
