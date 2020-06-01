import React, {forwardRef, useCallback, MouseEventHandler} from "react"

// eslint-disable-next-line react/display-name
export const FocusableDiv = forwardRef<HTMLDivElement>((
  {onClick, ...props}: React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement>,
  ref,
) => {

  const clickHandler: MouseEventHandler<HTMLDivElement> = useCallback(
    (event) => {
      event.currentTarget?.focus()
      onClick?.(event)
    },
    [onClick],
  )

  return <div tabIndex={-1} {...props} onClick={clickHandler} ref={ref}/>
})
