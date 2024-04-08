import { styled } from "@mui/material";
import { useStateWithRevertTimeout } from "./useStateWithRevertTimeout";
import { useSelector } from "react-redux";
import { getLoggedUser, getTabs } from "../../reducers/selectors/settings";
import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { TruncatedList } from "react-truncate-list";
import "react-truncate-list/dist/styles.css";
import { css } from "@emotion/css";
import { TabElement } from "./TabElement";
import Arrow from "../../assets/img/arrows/arrow-left.svg";
import { createPortal } from "react-dom";
import { useIntersectionObserverRef, useKey } from "rooks";
import FocusLock from "react-focus-lock";
import { alpha } from "../../containers/theme/helpers";

const PlainButton = styled("button")({
    background: "unset",
    border: "unset",
    padding: "unset",
    margin: "unset",
});

export const PlainLink = styled(TabElement)({
    "&, &:hover, &:focus": {
        color: "inherit",
        textDecoration: "none",
    },
});

const List = styled(TruncatedList)({
    flex: 1,
    display: "flex",
    padding: ".01px", // avoid size rounding problem (flickering of list elements) for stupid zoom values
    li: {
        // expand leftmost elements to force right alignment
        "&:nth-of-type(1), &:nth-of-type(2)": {
            flex: 1,
            justifyContent: "flex-end",
        },
        // less than 2 elements not allowed
        "&:nth-of-type(3)": {
            marginLeft: "50%",
        },
        "&:not([hidden])": {
            display: "flex",
            alignItems: "center",
        },
    },
});

const Popup = styled(FocusLock)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    alignItems: "stretch",
    textAlign: "right",
    zIndex: 1501,
    position: "absolute",
    inset: "3em 0 auto auto",
    background: alpha(theme.custom.colors.secondaryBackground, 0.8),
    backdropFilter: "blur(4px)",
}));

function ExpandButton({ children }: PropsWithChildren<unknown>) {
    const [expanded, setExpanded] = useStateWithRevertTimeout(false);
    const [ref] = useIntersectionObserverRef(([entry]) => {
        setExpanded((expanded) => expanded && entry.isIntersecting);
    });

    useKey(["Escape"], () => {
        setExpanded(false);
    });

    return (
        <PlainButton
            className={css({
                display: "flex",
                alignSelf: "stretch",
                alignItems: "center",
            })}
            onClick={() => setExpanded((v) => !v)}
            ref={ref}
        >
            <Arrow
                className={css({
                    transform: `rotate(${expanded ? 90 : 270}deg)`,
                    width: "2em",
                    height: "2em",
                })}
            />
            {expanded && createPortal(<Popup returnFocus>{children}</Popup>, document.body)}
        </PlainButton>
    );
}

export function Menu(): JSX.Element {
    const tabs = useSelector(getTabs);
    const loggedUser = useSelector(getLoggedUser);

    const elements = useMemo(
        () =>
            tabs
                .filter((t) => !t.requiredPermission || loggedUser.hasGlobalPermission(t.requiredPermission))
                .filter((t) => !!t.title)
                .map((tab) => (
                    <PlainLink
                        className={css({
                            fontWeight: 400,
                            padding: ".8em 1.2em",
                            "&.active": {
                                background: "rgba(0, 0, 0, 0.3)",
                            },
                        })}
                        key={tab.id}
                        tab={tab}
                    />
                )),
        [loggedUser, tabs],
    );

    const renderTruncator = useCallback(
        ({ hiddenItemsCount }) => <ExpandButton>{elements.slice(-hiddenItemsCount)}</ExpandButton>,
        [elements],
    );

    return <List renderTruncator={renderTruncator}>{elements}</List>;
}
