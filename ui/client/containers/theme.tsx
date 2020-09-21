/* eslint-disable i18next/no-literal-string */
import Color from "color"
import {css} from "emotion"
import {ThemeProvider, ThemeProviderProps, useTheme} from "emotion-theming"
import React, {useMemo} from "react"
import vars from "../stylesheets/_variables.styl"

const {
  borderRadius, formControllHeight, fontSize, primary,
  warningColor: warning,
  errorColor: error,
  okColor: ok,
  sucessColor: sucess,
} = vars

function tint(base: string, amount = 0) {
  return Color(base).mix(Color("white"), amount).hsl().string()
}

export function tintPrimary(base) {
  return {
    primary: tint(base, 0),
    primary75: tint(base, 0.75),
    primary50: tint(base, 0.5),
    primary25: tint(base, 0.25),
  }
}

const defaultAppTheme = {
  themeClass: "",
  borderRadius: parseFloat(borderRadius),
  colors: {
    ...tintPrimary(primary),
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

    borderColor: "hsl(0, 0%, 30%)",
    canvasBackground: "hsl(0, 0%, 100%)",
    primaryBackground: "hsl(0, 0%, 100%)",
    secondaryBackground: "hsl(0, 0%, 100%)",
    primaryColor: "hsl(0, 0%, 10%)",
    secondaryColor: "hsl(0, 0%, 30%)",
    mutedColor: "hsl(0, 0%, 50%)",

    focusColor: primary,
    evenBackground: "hsl(0, 0%, 80%)",

    selectedValue: primary,
    accent: primary,
    warning, error, ok, sucess,
  },
  spacing: {
    controlHeight: parseFloat(formControllHeight),
    baseUnit: 4,
  },
  fontSize: parseFloat(fontSize),
}

export type NkTheme = typeof defaultAppTheme

export function NkThemeProvider({theme = defaultAppTheme, ...props}: Partial<ThemeProviderProps<NkTheme>>) {
  return <ThemeProvider<NkTheme> theme={theme} {...props}/>
}

export const useNkTheme = () => {
  const theme = useTheme<NkTheme>()

  const withFocus = useMemo(() => css({
    ":focus, :active:focus": {
      outline: "none",
      borderColor: theme?.colors?.focusColor,
      boxShadow: `0 0 0 1px ${theme?.colors?.focusColor}`,
    },
  }), [theme])

  return {theme, withFocus}
}
