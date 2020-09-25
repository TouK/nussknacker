import {cx} from "emotion"
import React from "react"
import styles from "./ListSeparator.styl"

export function ListSeparator(props: {dark?: boolean}): JSX.Element {
  return (
    <hr className={cx(
      styles.listSeparator,
      props.dark && styles.dark,
    )}
    />
  )
}
