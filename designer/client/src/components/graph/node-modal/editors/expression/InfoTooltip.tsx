import React, { useState, useRef } from "react";
import { ClickAwayListener, styled, Theme, Tooltip } from "@mui/material";
import InfoIcon from "@mui/icons-material/Info";
import { MarkdownStyled } from "../../MarkdownStyled";
import { getBorderColor } from "../../../../../containers/theme/helpers";

const StyledInfoIcon = styled(InfoIcon)(({ theme }) => ({
    cursor: "pointer",
    width: "1rem",
    height: "1rem",
    backgroundColor: theme.palette.background.paper,
}));

interface Props {
    text: string;
}

export const InfoTooltip = ({ text }: Props) => {
    const [tooltipOpen, setTooltipOpen] = useState(false);
    const tooltipRef = useRef<HTMLDivElement>(null);

    const handleIconClick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setTooltipOpen((prev) => !prev);
    };

    const handleClickAway = (event: MouseEvent) => {
        // Don't close the tooltip if clicking inside the tooltip content
        if (tooltipRef.current && tooltipRef.current.contains(event.target as Node)) {
            return;
        }
        setTooltipOpen(false);
    };

    return (
        <ClickAwayListener onClickAway={handleClickAway}>
            <Tooltip
                title={
                    <div ref={tooltipRef}>
                        <MarkdownStyled sx={{ fontSize: "0.75rem" }}>{text}</MarkdownStyled>
                    </div>
                }
                placement={"bottom-start"}
                arrow
                open={tooltipOpen}
                onClose={() => setTooltipOpen(false)}
                disableFocusListener
                disableHoverListener
                disableTouchListener
                componentsProps={{
                    tooltip: {
                        sx: (theme: Theme) => ({
                            fontSize: "0.75rem",
                            backgroundColor: theme.palette.background.paper,
                            outline: `1px solid ${getBorderColor(theme)}`,
                            maxWidth: "none",
                        }),
                    },
                    arrow: {
                        sx: (theme: Theme) => ({
                            color: getBorderColor(theme),
                        }),
                    },
                }}
            >
                <StyledInfoIcon onClick={handleIconClick} />
            </Tooltip>
        </ClickAwayListener>
    );
};
