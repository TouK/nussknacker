import React, { useCallback } from "react";
import { Box, Button, lighten, styled } from "@mui/material";
import { useWindows, WindowKind } from "../../../windowManager";

const StyledFooterButton = styled(Button)(({ theme }) => ({
    textTransform: "none",
    padding: `${theme.spacing(0.5)} ${theme.spacing(2)}`,
    flex: 1,
    backgroundColor: lighten(theme.palette.background.paper, 0.2),
    ...theme.typography.caption,
    color: theme.palette.getContrastText(lighten(theme.palette.background.paper, 0.2)),
    "&:hover": {
        backgroundColor: theme.palette.action.hover,
    },
}));

export const ActivitiesPanelFooter = () => {
    const { open } = useWindows();

    const handleOpenAddComment = useCallback(() => {
        open({
            title: "Add comment",
            isModal: true,
            shouldCloseOnEsc: true,
            kind: WindowKind.addComment,
        });
    }, [open]);

    const handleOpenAddAttachment = useCallback(() => {
        open({
            title: "Add attachment",
            isModal: true,
            shouldCloseOnEsc: true,
            kind: WindowKind.addAttachment,
        });
    }, [open]);

    return (
        <Box my={2} mx={1} display={"flex"} justifyContent={"space-between"} gap={1.5}>
            <StyledFooterButton variant={"contained"} onClick={handleOpenAddComment}>
                Add comment
            </StyledFooterButton>
            <StyledFooterButton variant={"contained"} onClick={handleOpenAddAttachment}>
                Add attachment
            </StyledFooterButton>
        </Box>
    );
};
