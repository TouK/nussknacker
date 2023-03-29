/* eslint-disable i18next/no-literal-string */
import {css} from "@emotion/css"
import {Global, Theme, ThemeProvider, ThemeProviderProps, useTheme} from "@emotion/react"
import Color from "color"
import React, {useMemo} from "react"
import {darkTheme} from "./darkTheme"
import {defaultAppTheme} from "./defaultAppTheme"

type DeepPartial<T> = {
  [P in keyof T]?: DeepPartial<T[P]>;
}

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
