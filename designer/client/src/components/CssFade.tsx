import { styled } from "@mui/material";
import { isFunction } from "lodash";
import type { ReactNode } from "react";
import React, { useCallback, useMemo } from "react";
import { CSSTransition } from "react-transition-group";

const CSSFadeStyled = styled(CSSTransition)`
    opacity: inherit;
    .group {
        position: relative;
        width: 100%;
        height: 100%;
    }
    &-exit,
    &-enter {
        .group > & {
            position: absolute;
            top: 0;
            bottom: 0;
            left: 0;
            right: 0;
        }
    }

    &-enter > * {
        opacity: 0;
    }

    &-enter-active > * {
        opacity: 1;
        transition: opacity 300ms ease-in;
    }

    &-exit {
        opacity: 1;
    }

    &-exit-active > * {
        opacity: 0;
        transition: opacity 300ms ease-in;
    }
`;

export function CssFade(props: { key: string; children: ReactNode }) {
    const addEndListener = useCallback((nodeOrDone: HTMLElement | (() => void), done?: () => void) => {
        !isFunction(nodeOrDone) ? nodeOrDone.addEventListener("transitionend", done, false) : nodeOrDone();
    }, []);

    const timeout = useMemo(() => ({ enter: 500, appear: 500, exit: 500 }), []);

    return <CSSFadeStyled timeout={timeout} addEndListener={addEndListener} {...props} />;
}
