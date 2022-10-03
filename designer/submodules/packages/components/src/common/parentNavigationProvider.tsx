import { Link, LinkProps } from "@mui/material";
import React, {
    createContext,
    ForwardedRef,
    forwardRef,
    MouseEventHandler,
    PropsWithChildren,
    useCallback,
    useContext,
} from "react";
import { makeRelative } from "./scenarioHref";

export interface Navigation {
    onNavigate?: (path: string | number) => void;
}

const NavigationContext = createContext<Navigation>({});

export function NavigationProvider(props: PropsWithChildren<{ navigation: Navigation }>): JSX.Element {
    return <NavigationContext.Provider value={props.navigation}>{props.children}</NavigationContext.Provider>;
}

const isRelativeRe = /^\/\w/;

export const ExternalLink = forwardRef(function ExternalLink(
    props: Omit<LinkProps<"a">, "onClick">,
    ref: ForwardedRef<HTMLAnchorElement>,
): JSX.Element {
    const { onNavigate } = useContext(NavigationContext);
    const { href, target } = props;
    const onClick: MouseEventHandler<HTMLAnchorElement> = useCallback(
        (e) => {
            const isModified = e.altKey || e.ctrlKey || e.metaKey || e.shiftKey;
            const isPlainClick = e.button === 0 && !isModified;
            if (isPlainClick && onNavigate && !target) {
                const relative = makeRelative(href);
                if (isRelativeRe.test(relative)) {
                    e.preventDefault();
                    onNavigate(relative);
                }
            }
        },
        [onNavigate, href, target],
    );
    return <Link ref={ref} {...props} onClick={onClick} />;
});
