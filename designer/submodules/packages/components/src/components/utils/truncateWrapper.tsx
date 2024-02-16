import { Visibility } from "@mui/icons-material";
import { Box, Popover, PopoverOrigin, Stack, styled, Typography } from "@mui/material";
import { alpha } from "@mui/material/styles";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { bindPopover, bindTrigger, PopupState, usePopupState } from "material-ui-popup-state/hooks";
import React, { PropsWithChildren, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Truncate } from "./truncate";

const paperProps = {
    sx: {
        border: 1,
        borderColor: "primary.dark",
        maxWidth: "60vw",
        a: {
            maxWidth: "50vw",
        },
    },
};

const anchorOrigin: PopoverOrigin = {
    vertical: "center",
    horizontal: "center",
};
const transformOrigin: PopoverOrigin = {
    vertical: "center",
    horizontal: "center",
};

const TruncateButton = styled("button")(({ theme }) => ({
    background: "none",
    border: "none",
    color: theme.palette.common.white,
    display: "flex",
    alignItems: "center",
    textTransform: "lowercase",
    margin: "0 2px",
    height: "100%",
    padding: 0,
    ":hover": {
        backgroundColor: alpha(theme.palette.common.white, 0.08),
    },
    ":focus-visible": {
        outline: "none",
        backgroundColor: alpha(theme.palette.common.white, 0.08),
    },
}));

const Truncator = ({
    itemsCount,
    hiddenItemsCount,
    popupState,
}: {
    itemsCount: number;
    hiddenItemsCount: number;
    popupState: PopupState;
}) => {
    const { t } = useTranslation();
    return (
        <TruncateButton {...bindTrigger(popupState)} className="truncator">
            <Visibility sx={{ fontSize: "18px", color: "rgb(224, 224, 224)" }} />
            <Typography sx={{ mx: "4px", fontSize: "13px" }}>
                {itemsCount === hiddenItemsCount
                    ? t("truncator.allHidden", "{{hiddenItemsCount}} items...", { hiddenItemsCount })
                    : t("truncator.someHidden", "{{hiddenItemsCount}} more...", { hiddenItemsCount })}
            </Typography>
        </TruncateButton>
    );
};

export function TruncateWrapper({ children }: PropsWithChildren<GridRenderCellParams>): JSX.Element {
    const popupState = usePopupState({ variant: "popover", popupId: "pop" });
    const { anchorEl, ...popoverProps } = bindPopover(popupState);
    const ref = useRef();

    const childrenNumber = React.Children.count(children);
    const renderTruncator = useCallback(
        ({ hiddenItemsCount }) => <Truncator itemsCount={childrenNumber} hiddenItemsCount={hiddenItemsCount} popupState={popupState} />,
        [childrenNumber, popupState],
    );

    return (
        <Box ref={ref} overflow="hidden" flex={1}>
            <Stack
                flex={1}
                direction="row"
                spacing={0.5}
                component={Truncate}
                renderTruncator={renderTruncator}
                sx={{
                    "&& li:nth-of-type(1)": {
                        marginLeft: 0,
                    },
                }}
            >
                {children}
            </Stack>
            <Popover
                elevation={3}
                PaperProps={paperProps}
                anchorOrigin={anchorOrigin}
                transformOrigin={transformOrigin}
                {...popoverProps}
                open={popoverProps.open}
                anchorEl={ref.current || anchorEl}
            >
                <Box display="flex" flexDirection="row" flexWrap="wrap" p={2} gap={0.5} justifyContent="flex-end">
                    {children}
                </Box>
            </Popover>
        </Box>
    );
}
