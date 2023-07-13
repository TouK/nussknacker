/* eslint-disable i18next/no-literal-string */
import React, { PropsWithChildren } from "react";

export const tintPrimary = jest.fn();

export const NkThemeProvider = ({ children }: PropsWithChildren<unknown>) => <>{children}</>;

export const useNkTheme = () => ({ theme: {}, withFocus: "withFocus" });
