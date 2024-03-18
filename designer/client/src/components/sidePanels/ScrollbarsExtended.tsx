/* eslint-disable i18next/no-literal-string */
import React, { PropsWithChildren, useState, useEffect } from "react";
import Scrollbars from "react-scrollbars-custom";
import { lighten, styled } from "@mui/material";
import { Side, PanelSide } from "./SidePanel";

const SCROLLBAR_WIDTH = 40; //some value bigger than real scrollbar width
const CLEAN_STYLE = null;
const SCROLL_THUMB_SIZE = 8;
const TOOLBARS_GAP = 3;

const trackStyleProps = (side: Side) => ({
    background: CLEAN_STYLE,
    borderRadius: SCROLL_THUMB_SIZE,
    backgroundColor: "transparent",
    width: SCROLL_THUMB_SIZE - 1,
    top: TOOLBARS_GAP - SCROLL_THUMB_SIZE / 3,
    bottom: TOOLBARS_GAP - SCROLL_THUMB_SIZE / 3,
    height: CLEAN_STYLE,
    right: side === PanelSide.Left ? 0 : null,
    left: side === PanelSide.Right ? 0 : null,
});

const thumbYStyleProps = {
    borderRadius: CLEAN_STYLE,
    cursor: CLEAN_STYLE,
    backgroundColor: "rgba(0,0,0, 0.45)",
};

const scrollerStyleProps = { padding: CLEAN_STYLE, display: "flex" };

const ScrollbarsWrapper = styled("div")(({ isScrollPossible }: { isScrollPossible: boolean }) => ({ theme }) => ({
    minHeight: "100%",
    display: "flex",
    transition: "all .25s",
    overflow: "hidden",
    background: isScrollPossible && lighten(theme.palette.background.paper, 0.4),
    pointerEvents: isScrollPossible ? "auto" : "inherit",
}));

interface ScrollbarsExtended {
    onScrollToggle?: (isEnabled: boolean) => void;
    side: Side;
}

export function ScrollbarsExtended({ children, onScrollToggle, side }: PropsWithChildren<ScrollbarsExtended>) {
    const [isScrollPossible, setScrollPossible] = useState<boolean>();

    useEffect(() => {
        onScrollToggle?.(isScrollPossible);
    }, [isScrollPossible]);

    return (
        <Scrollbars
            noScrollX
            style={{
                pointerEvents: isScrollPossible ? "auto" : "inherit",
            }}
            disableTracksWidthCompensation
            trackYProps={{ style: trackStyleProps(side) }}
            thumbYProps={{ style: thumbYStyleProps }}
            contentProps={{ style: scrollerStyleProps }}
            scrollbarWidth={SCROLLBAR_WIDTH}
            scrollerProps={{ style: { marginRight: -SCROLLBAR_WIDTH } }}
            onUpdate={({ scrollYPossible }) => setScrollPossible(scrollYPossible)}
        >
            <ScrollbarsWrapper isScrollPossible={isScrollPossible}>{children}</ScrollbarsWrapper>
        </Scrollbars>
    );
}
