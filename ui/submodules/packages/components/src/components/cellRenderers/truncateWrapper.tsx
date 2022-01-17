import React, { PropsWithChildren, useCallback, useRef } from "react";
import { useCellArrowKeys } from "./useCellArrowKeys";
import { Truncate } from "./truncate";
import { Visibility } from "@mui/icons-material";
import { Box, Chip, Popover, Stack } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { bindPopover, bindTrigger, usePopupState } from "material-ui-popup-state/hooks";
import "react-truncate-list/dist/styles.css";

export function TruncateWrapper({ children, ...props }: PropsWithChildren<GridRenderCellParams>): JSX.Element {
    const handleCellKeyDown = useCellArrowKeys(props);
    const popupState = usePopupState({ variant: "popover", popupId: "pop" });
    const { anchorEl, ...popoverProps } = bindPopover(popupState);
    const ref = useRef();

    const renderTruncator = useCallback(({ hiddenItemsCount }) => (
        <Chip
            sx={{ border: "none" }}
            tabIndex={0}
            icon={<Visibility />}
            label={`${hiddenItemsCount} more...`}
            size="small"
            variant="outlined"
            {...bindTrigger(popupState)}
        />
    ), [popupState]);

    return (
        <Box
            ref={ref}
            onKeyDown={handleCellKeyDown}
            sx={{
                overflow: "hidden",
                flex: 1,
            }}
        >
            <Stack
                sx={{ flex: 1 }}
                direction="row"
                spacing={0.5}
                component={Truncate}
                renderTruncator={renderTruncator}
            >
                {children}
            </Stack>
            <Popover
                elevation={3}
                PaperProps={{
                    sx: {
                        border: 1,
                        borderColor: "primary.dark",
                        maxWidth: "60vw",
                        a: {
                            maxWidth: "50vw",
                        },
                    },
                }}
                anchorOrigin={{
                    vertical: "center",
                    horizontal: "center",
                }}
                transformOrigin={{
                    vertical: "center",
                    horizontal: "center",
                }}
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
