import type { Theme as MUITheme } from "@mui/material/styles";
import { useTheme } from "@mui/material/styles";
import type { CSSProperties } from "react";
import React from "react";

type StyleProp<Props, Theme> = CSSProperties | ((theme: Theme, props: Props) => CSSProperties);

type StylesWrapperProps<C extends React.ElementType, Theme = MUITheme> = {
    component: C;
    style?: StyleProp<React.ComponentProps<C>, Theme>;
} & React.ComponentProps<C>;

export function ThemedStylesWrapper<C extends React.ElementType, Theme = MUITheme>({
    component: Component,
    style,
    ...props
}: StylesWrapperProps<C, Theme>) {
    const theme = useTheme() as Theme;

    const computedStyle: CSSProperties = typeof style === "function" ? style(theme, props) : style || {};

    return <Component {...(props as React.ComponentProps<C>)} style={computedStyle} />;
}
