import {useSelector} from "react-redux"
import {getEnvironmentAlert} from "../reducers/selectors/settings"
import React, {useMemo} from "react"
import styled from "@emotion/styled"

// TODO: get rid of 'indicator-', maybe rename to "warn", "prod" etc.
export enum EnvironmentTagColor {
  green = "indicator-green",
  red = "indicator-red",
  blue = "indicator-blue",
  yellow = "indicator-yellow"
}

const Tag = styled.div(
  {
    display: "inline-block",
    padding: "3px 8px",
    marginBottom: 0,
    fontSize: 15,
    fontWeight: "normal",
    textAlign: "center",
    whiteSpace: "nowrap",
    verticalAlign: "middle",
    backgroundImage: "none",
    borderRadius: "3px",
    overflow: "hidden",
    textOverflow: "ellipsis",
    letterSpacing: "0.2px",
    color: "hsl(0,0%,100%)",
  },
  ({color}) => ({
    backgroundColor: color,
  })
)

export function EnvironmentTag() {
  const {content, color} = useSelector(getEnvironmentAlert)
  const background = useMemo(
    () => {
      switch (color) {
        case EnvironmentTagColor.green:
          return "hsl(120,39%,54%)"
        case EnvironmentTagColor.blue:
          return "hsl(194,66%,61%)"
        case EnvironmentTagColor.red:
          return "hsl(2,64%,58%)"
        case EnvironmentTagColor.yellow:
          return "hsl(35,84%,62%)"
        default:
          return color
      }
    },
    [color]
  )

  if (!content) {
    return null
  }

  return (
    <Tag color={background} title={content}>
      {content}
    </Tag>
  )
}
