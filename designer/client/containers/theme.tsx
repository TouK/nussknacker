/* eslint-disable i18next/no-literal-string */
import {css} from "@emotion/css"
import {Global, Theme, ThemeProvider, ThemeProviderProps, useTheme} from "@emotion/react"
import Color from "color"
import React, {useMemo} from "react"
import vars from "../stylesheets/_variables.styl"
import {darkTheme} from "./darkTheme"

type DeepPartial<T> = {
  [P in keyof T]?: DeepPartial<T[P]>;
}

const {
  borderRadius, formControllHeight, fontSize, primary,
  warningColor: warning,
  errorColor: error,
  okColor: ok,
  sucessColor: sucess,
} = vars

export function tint(base: string, amount = 0): string {
  return Color(base).mix(Color("white"), amount).hsl().string()
}

export function alpha(base: string, amount = 1): string {
  return Color(base).alpha(amount).hsl().string()
}

export function tintPrimary(base: string): {
  primary75: string,
  primary25: string,
  primary50: string,
  primary: string,
} {
  return {
    primary: tint(base, 0),
    primary75: tint(base, 0.75),
    primary50: tint(base, 0.5),
    primary25: tint(base, 0.25),
  }
}

export const defaultAppTheme = {
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
    warning,
    error,
    ok,
    sucess,
    warn: warning,
    info: primary,
  },
  spacing: {
    controlHeight: parseFloat(formControllHeight),
    baseUnit: 4,
  },
  fontSize: parseFloat(fontSize),
}

export type NkTheme = DeepPartial<typeof defaultAppTheme>

declare module "@emotion/react" {
  // eslint-disable-next-line @typescript-eslint/no-empty-interface
  interface Theme extends NkTheme {
  }
}

export function NkThemeProvider({theme = darkTheme, ...props}: Partial<ThemeProviderProps>): JSX.Element {
  return <ThemeProvider theme={theme} {...props}/>
}

export const useNkTheme: () => { withFocus: string, theme: NkTheme } = () => {
  const theme = useTheme()

  const withFocus = useMemo(() => css({
    ":focus, :active:focus": {
      outline: "none",
      borderColor: theme?.colors?.focusColor,
      boxShadow: `0 0 0 1px ${theme?.colors?.focusColor}`,
    },
  }), [theme])

  return {theme, withFocus}
}

export function getContrastColor(color: string, contrast = 5, ratio = .1) {
  const baseColor = Color(color)
  const resultColor = baseColor.isDark() ? baseColor.lighten(ratio) : baseColor.darken(ratio)
  if (baseColor.contrast(resultColor) < contrast && resultColor.lightness() % 100 !== 0) {
    return getContrastColor(color, contrast, ratio * 1.1)
  }
  return resultColor.hex()
}

export function getDarkenContrastColor(color: string, contrast = 5, ratio = .1) {
  const baseColor = Color(color)
  const resultColor = baseColor.darken(ratio)
  if (baseColor.contrast(resultColor) < contrast && resultColor.lightness() > 0) {
    return getDarkenContrastColor(color, contrast, ratio * 1.1)
  }
  return resultColor.hex()
}

function parseVarName(name: string) {
  return name.startsWith("--") ? name : `--${name}`
}

function parseColorName(name: string) {
  return name.toLowerCase().endsWith("color") ? name : `${name}Color`
}

function colorsToVariables(colors: Record<string, string>): Record<`--${string}`, string> {
  return Object.fromEntries(Object.entries(colors).map(
    ([name, value]) => [parseVarName(parseColorName(name)), value]
  ))
}

function themeToVariables(theme: Theme): { ":root": Record<`--${string}`, string> } {
  return {
    ":root": colorsToVariables(theme.colors),
  }
}

export const GlobalCSSVariables = () => (
  <Global styles={themeToVariables}/>
)
