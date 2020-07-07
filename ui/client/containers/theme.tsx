/* eslint-disable i18next/no-literal-string */
import React from "react"
import vars from "../stylesheets/_variables.styl"

const {
  borderRadius,

  primary,
  primary75,
  primary50,
  primary25,
} = vars

export const defaultAppTheme = {
  borderRadius: parseFloat(borderRadius),
  inputHeight: 45,
  fontSize: 14,

  colors: {
    primary,
    primary75,
    primary50,
    primary25,
  },
}

const [d, d1, d2, d3, d4, base, l4, l3, l2, l1, l] = [
  "#000000", "#1A1A1A", "#333333", "#4D4D4D", "#666666", "#808080", "#999999", "#B3B3B3", "#CCCCCC", "#E6E6E6", "#FFFFFF",
]

const newTheme = {
  // canvasBackground: l3,
  // primaryBackground: d3,
  // secondaryBackground: d2,
  // primaryColor: l,
  // secondaryColor: l2,
  mutedColor: base,

  focusColor: d1,
  evenBackground: d3,

  selectedValue: d2,
  accent: "hsl(90,30%,40%)",
}
