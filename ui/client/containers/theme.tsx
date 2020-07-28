/* eslint-disable i18next/no-literal-string */
import {css} from "emotion"
import {ThemeProvider, ThemeProviderProps, useTheme} from "emotion-theming"
import React, {useMemo} from "react"
import vars from "../stylesheets/_variables.styl"

const {
  borderRadius,
  formControllHeight,
  fontSize,

  primary,
  primary75,
  primary50,
  primary25,
} = vars

export const defaultAppTheme = {
  borderRadius: parseFloat(borderRadius),
  colors: {
    primary,
    primary75,
    primary50,
    primary25,
    danger: "#DE350B",
    dangerLight: "#FFBDAD",
    neutral0: "hsl(0, 0%, 100%)",
    neutral5: "hsl(0, 0%, 95%)",
    neutral10: "hsl(0, 0%, 90%)",
    neutral20: "hsl(0, 0%, 80%)",
    neutral30: "hsl(0, 0%, 70%)",
    neutral40: "hsl(0, 0%, 60%)",
    neutral50: "hsl(0, 0%, 50%)",
    neutral60: "hsl(0, 0%, 40%)",
    neutral70: "hsl(0, 0%, 30%)",
    neutral80: "hsl(0, 0%, 20%)",
    neutral90: "hsl(0, 0%, 10%)",
  },
  spacing: {
    controlHeight: parseFloat(formControllHeight),
  },
  fontSize: parseFloat(fontSize),
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
  accent: "#668547",
}

export type NkTheme = typeof defaultAppTheme

export function NkThemeProvider({theme = defaultAppTheme, ...props}: Partial<ThemeProviderProps<NkTheme>>) {
  return <ThemeProvider<NkTheme> theme={theme} {...props}/>
}

export const useNkTheme = () => {
  const theme = useTheme<NkTheme>()

  const withFocus = useMemo(() => css({
    ":focus": {
      outline: "none",
      borderColor: theme?.colors?.primary,
      boxShadow: `0 0 0 1px ${theme?.colors?.primary}`,
    },
  }), [theme])

  return {theme, withFocus}
}
