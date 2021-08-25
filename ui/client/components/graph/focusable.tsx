import {css, cx} from "emotion"
import {debounce} from "lodash"
import React, {forwardRef, MouseEventHandler, useCallback, useMemo} from "react"
import {useSizeWithRef} from "../../containers/hooks/useSize"

interface ContainerProps extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
  onResize?: (current: DOMRectReadOnly) => void,
}

export const GraphPaperContainer = forwardRef<HTMLDivElement, ContainerProps>(({onClick, className, onResize, ...props}, forwardedRef) => {
  const clickHandler: MouseEventHandler<HTMLDivElement> = useCallback(
    (event) => {
      event.currentTarget?.focus()
      onClick?.(event)
    },
    [onClick],
  )

  const options = useMemo(() => ({
    onResize: debounce(({entry}) => {
      onResize?.(entry.contentRect)
    }, 500),
  }
  ), [onResize])

  const {observe} = useSizeWithRef(forwardedRef, options)

  const styles = css({
    minHeight: 200,
    ".Page > &": {
      overflow: "hidden",
      width: "100%",
      height: "100%",
    },
  })

  return (
    <div
      className={cx(styles, className)}
      ref={onResize ? observe : forwardedRef}
      tabIndex={-1}
      onClick={clickHandler}
      {...props}
    />
  )
})
