/* eslint-disable i18next/no-literal-string */
import React from "react"

export const tintPrimary = jest.fn()

export const NkThemeProvider = ({children}) => <>{children}</>

export const useNkTheme = () => ({theme: {}, withFocus: "withFocus"})
