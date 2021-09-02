import React, {memo} from "react"

export interface ItemsProps<I> {
  items: {item: I, el: JSX.Element}[],
}

export const Items = memo(function Items<I extends any>(props: ItemsProps<I>): JSX.Element {
  return (
    <>
      {props.items.map(({el}) => el)}
    </>
  )
})
