import vars from "../stylesheets/darkColors.styl"
import {NkTheme, tintPrimary} from "./theme"

const {borderRadius, marginSize} = vars

const [d, d1, d2, d3, d4, base, l4, l3, l2, l1, l] = [
  // eslint-disable-next-line i18next/no-literal-string
  "#000000", "#1A1A1A", "#333333", "#4D4D4D", "#666666", "#808080", "#999999", "#B3B3B3", "#CCCCCC", "#E6E6E6", "#FFFFFF",
]

const colors = {
  borderColor: d,
  canvasBackground: l3,
  primaryBackground: d3,
  secondaryBackground: d2,
  primaryColor: l,
  secondaryColor: l2,
  mutedColor: base,
  focusColor: d1,
  evenBackground: d3,
  selectedValue: d2,
  accent: "#668547",
}

const selectColors = {
  ...tintPrimary(colors.focusColor),
  neutral0: colors.secondaryBackground,
  neutral5: colors.secondaryColor,
  neutral10: colors.accent,
  neutral20: colors.mutedColor,
  neutral30: colors.borderColor,
  neutral40: colors.secondaryColor,
  neutral50: colors.mutedColor,
  neutral60: colors.mutedColor,
  neutral70: colors.secondaryColor,
  neutral80: colors.primaryColor,
  neutral90: colors.secondaryColor,
}

export const darkTheme: NkTheme = {
  themeClass: vars.darkTheme,
  borderRadius: parseFloat(borderRadius),
  spacing: {
    controlHeight: 36,
  },
  colors: {
    ...selectColors,
    ...colors,
  },
}
