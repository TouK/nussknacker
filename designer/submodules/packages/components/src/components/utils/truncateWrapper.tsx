import React, { PropsWithChildren, useCallback, useRef } from "react";
import { Truncate } from "./truncate";
import { Visibility } from "@mui/icons-material";
import { Box, Popover, PopoverOrigin, Stack, Typography } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { bindPopover, bindTrigger, PopupState, usePopupState } from "material-ui-popup-state/hooks";
import { useTranslation } from "react-i18next";
import { alpha } from "@mui/material/styles";

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
                itemClassName="item"
                truncatorClassName="truncator"
                sx={{
                    "&& .item:nth-of-type(1)": {
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
        <Box
            component={"button"}
            sx={(theme) => ({
                background: "none",
                border: "none",
                display: "flex",
                alignItems: "center",
                textTransform: "lowercase",
                mx: "2px",
                padding: 0,
                height: "24px",
                ":hover": {
                    backgroundColor: alpha(theme.palette.common.white, 0.08),
                },
                ":focus-visible": {
                    outline: "none",
                    backgroundColor: alpha(theme.palette.common.white, 0.08),
                },
            })}
            color={"inherit"}
            {...bindTrigger(popupState)}
        >
            <Visibility sx={{ fontSize: "18px", color: "rgb(224, 224, 224)" }} />
            <Typography sx={{ mx: "4px", fontSize: "13px" }}>
                {itemsCount === hiddenItemsCount
                    ? t("truncator.allHidden", "{{hiddenItemsCount}} items...", { hiddenItemsCount })
                    : t("truncator.someHidden", "{{hiddenItemsCount}} more...", { hiddenItemsCount })}
            </Typography>
        </Box>
    );
};
