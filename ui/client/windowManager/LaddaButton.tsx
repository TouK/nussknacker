import {css, cx} from "emotion"
import "ladda/dist/ladda.min.css"
import React from "react"
import Button from "react-ladda"
import {useNkTheme} from "../containers/theme"

export const LaddaButton = ({className, ...props}): JSX.Element => {
  const {withFocus} = useNkTheme()
  return (
    <Button
      {...props}
      className={cx(css({padding: 0, outline: "none"}), withFocus, className)}
    />
  )
}
