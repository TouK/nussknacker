import { CheckOutlined, CloseOutlined, DeleteOutlined } from "@mui/icons-material";
import { Box, ClickAwayListener, useTheme } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { StyledButton } from "../../../../../styledButton";
import { InfoTooltip } from "../../../../editors/InfoTooltip/InfoTooltip";

type Props = {
    isConfirming: boolean;
    isDisabled: boolean;
    disabledTooltip: string | undefined;
    startDeleting: () => void;
    cancelDelete: () => void;
    confirmDelete: () => void;
};

export const DeleteButton = ({ isConfirming, isDisabled, disabledTooltip, startDeleting, cancelDelete, confirmDelete }: Props) => {
    const { t } = useTranslation();
    const { transitions } = useTheme();

    const slideInFromRight = {
        "@keyframes slideInFromRight": { from: { transform: "translateX(20px)", opacity: 0 } },
        animation: `slideInFromRight ${transitions.duration.shorter}ms ${transitions.easing.easeOut}`,
    };

    const slideInFromLeft = {
        "@keyframes slideInFromLeft": { from: { transform: "translateX(-20px)", opacity: 0 } },
        animation: `slideInFromLeft ${transitions.duration.shorter}ms ${transitions.easing.easeOut}`,
    };

    if (isConfirming) {
        return (
            <ClickAwayListener onClickAway={cancelDelete}>
                <Box display="flex" gap={1} sx={slideInFromRight}>
                    <InfoTooltip title={t("testCaseDelete.confirm", "Confirm deletion")} variant={"hover"} enterDelay={500}>
                        <StyledButton data-testid="confirm-delete-test-case" onClick={confirmDelete}>
                            <CheckOutlined fontSize="small" />
                        </StyledButton>
                    </InfoTooltip>
                    <InfoTooltip title={t("testCaseDelete.cancel", "Cancel deletion")} variant={"hover"} enterDelay={500}>
                        <StyledButton data-testid="cancel-delete-test-case" onClick={cancelDelete}>
                            <CloseOutlined fontSize="small" />
                        </StyledButton>
                    </InfoTooltip>
                </Box>
            </ClickAwayListener>
        );
    }

    return (
        <Box sx={slideInFromLeft}>
            <InfoTooltip title={disabledTooltip ?? t("testCaseDelete.delete", "Delete test case")} variant={"hover"} enterDelay={500}>
                <span>
                    <StyledButton data-testid="delete-test-case" onClick={startDeleting} disabled={isDisabled}>
                        <DeleteOutlined fontSize="small" />
                    </StyledButton>
                </span>
            </InfoTooltip>
        </Box>
    );
};
