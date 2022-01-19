import { Link, LinkProps } from "@mui/material";
import React, { createContext, ForwardedRef, forwardRef, PropsWithChildren, useCallback, useContext } from "react";

export interface Navigation {
    onNavigate?: (path: string | number) => void;
}

const NavigationContext = createContext<Navigation>({});

export function NavigationProvider(props: PropsWithChildren<{ navigation: Navigation }>): JSX.Element {
    return <NavigationContext.Provider value={props.navigation}>{props.children}</NavigationContext.Provider>;
}

export const ExternalLink = forwardRef(function ExternalLink(
    props: Omit<LinkProps<"a">, "onClick">,
    ref: ForwardedRef<HTMLAnchorElement>,
): JSX.Element {
    const { onNavigate } = useContext(NavigationContext);
    const onClick = useCallback(
        (e) => {
            const isModified = e.altKey || e.ctrlKey || e.metaKey || e.shiftKey;
            if (onNavigate && !isModified && !props.target) {
                e.preventDefault();
                onNavigate(props.href);
            }
        },
        [onNavigate, props.href, props.target],
    );
    return <Link ref={ref} {...props} onClick={onClick} />;
});
