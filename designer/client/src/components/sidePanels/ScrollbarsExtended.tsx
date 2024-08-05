/* eslint-disable i18next/no-literal-string */
import React, { PropsWithChildren, useEffect, useState } from "react";
import Scrollbars from "react-scrollbars-custom";
import { lighten, styled, Theme, useTheme } from "@mui/material";
import { PanelSide } from "../../actions/nk";
import { blendDarken } from "../../containers/theme/helpers";

const SCROLLBAR_WIDTH = 40; //some value bigger than real scrollbar width
const CLEAN_STYLE = null;
const SCROLL_THUMB_SIZE = 8;
const TOOLBARS_GAP = 3;

const trackStyleProps = (side: PanelSide) => ({
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

const thumbYStyleProps = (theme: Theme) => ({
    borderRadius: CLEAN_STYLE,
    cursor: CLEAN_STYLE,
    backgroundColor: lighten(theme.palette.background.paper, 0.4),
});

const scrollerStyleProps = { padding: CLEAN_STYLE, display: "flex" };

const ScrollbarsWrapper = styled("div")(({ isScrollPossible }: { isScrollPossible: boolean }) => ({ theme }) => ({
    minHeight: "100%",
    display: "flex",
    transition: "all .25s",
    overflow: "hidden",
    background: isScrollPossible && blendDarken(theme.palette.common.white, 0.75),
    pointerEvents: isScrollPossible ? "auto" : "inherit",
}));

interface ScrollbarsExtended {
    onScrollToggle?: (isEnabled: boolean) => void;
    side: PanelSide;
}

export function ScrollbarsExtended({ children, onScrollToggle, side }: PropsWithChildren<ScrollbarsExtended>) {
    const theme = useTheme();
    const [isScrollPossible, setScrollPossible] = useState<boolean>();

    useEffect(() => {
        onScrollToggle?.(isScrollPossible);
    }, [isScrollPossible, onScrollToggle]);

    return (
        <Scrollbars
            noScrollX
            style={{
                pointerEvents: isScrollPossible ? "auto" : "inherit",
            }}
            disableTracksWidthCompensation
            trackYProps={{ style: trackStyleProps(side) }}
            thumbYProps={{ style: thumbYStyleProps(theme) }}
            contentProps={{ style: scrollerStyleProps }}
            scrollbarWidth={SCROLLBAR_WIDTH}
            scrollerProps={{ style: { marginRight: -SCROLLBAR_WIDTH } }}
            onUpdate={({ scrollYPossible }) => setScrollPossible(scrollYPossible)}
        >
            <ScrollbarsWrapper isScrollPossible={isScrollPossible}>{children}</ScrollbarsWrapper>
        </Scrollbars>
    );
}
