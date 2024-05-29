import { styled, Typography } from "@mui/material";
import { useStateWithRevertTimeout } from "./useStateWithRevertTimeout";
import { useSelector } from "react-redux";
import { getLoggedUser, getTabs } from "../../reducers/selectors/settings";
import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { TruncatedList } from "react-truncate-list";
import { TabElement } from "./TabElement";
import Arrow from "../../assets/img/arrows/arrow-left.svg";
import { createPortal } from "react-dom";
import { useIntersectionObserverRef, useKey } from "rooks";
import FocusLock from "react-focus-lock";
import { EventTrackingSelector, getEventTrackingProps } from "../../containers/event-tracking";

const PlainButton = styled("button")({
    background: "unset",
    border: "unset",
    outline: "unset",
    padding: "unset",
    margin: "unset",
    "&:focus": {
        outline: "unset",
    },
});

const List = styled(TruncatedList)({
    // make sure to override global classname
    "&&": {
        boxSizing: "border-box",
        padding: ".05px", // avoid size rounding problem (flickering of list elements) for stupid zoom values
        margin: 0,
        listStyle: "none",
        overflow: "auto",
        flex: 1,
        display: "flex",
        "*, *::before, *::after": {
            boxSizing: "inherit",
        },
    },

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
    background: theme.palette.background.paper,
    backdropFilter: "blur(4px)",
}));

const StyledArrow = styled(Arrow)({
    width: "2em",
    height: "2em",
});

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
            sx={{
                display: "flex",
                alignSelf: "stretch",
                alignItems: "center",
            }}
            onClick={() => setExpanded((v) => !v)}
            ref={ref}
        >
            <StyledArrow
                style={{
                    transform: `rotate(${expanded ? 90 : 270}deg)`,
                }}
            />
            {expanded && createPortal(<Popup returnFocus>{children}</Popup>, document.body)}
        </PlainButton>
    );
}

const Spacer = styled("span")(({ theme }) => ({
    color: theme.palette.action.disabled,
    border: "1px solid",
    alignSelf: "stretch",
    "li &": {
        marginBlock: theme.spacing(1.5),
        marginInline: theme.spacing(1),
    },
}));

export function Menu(): JSX.Element {
    const tabs = useSelector(getTabs);
    const loggedUser = useSelector(getLoggedUser);

    const elements = useMemo(
        () =>
            tabs
                .filter((t) => !t.requiredPermission || loggedUser.hasGlobalPermission(t.requiredPermission))
                .filter((t) => !!t.title)
                .map((tab) => (
                    <React.Fragment key={tab.id}>
                        {tab.spacerBefore ? <Spacer /> : null}
                        <Typography
                            component={TabElement}
                            tab={tab}
                            {...(tab.id.toLowerCase() === "components"
                                ? getEventTrackingProps({ selector: EventTrackingSelector.ComponentsTab })
                                : tab.id.toLowerCase() === "metrics"
                                ? getEventTrackingProps({ selector: EventTrackingSelector.GlobalMetricsTab })
                                : null)}
                        />
                    </React.Fragment>
                )),
        [loggedUser, tabs],
    );

    const renderTruncator = useCallback(
        ({ hiddenItemsCount }) => <ExpandButton>{elements.slice(-hiddenItemsCount)}</ExpandButton>,
        [elements],
    );

    return <List renderTruncator={renderTruncator}>{elements}</List>;
}
