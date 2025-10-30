import { CloudOff } from "@mui/icons-material";
import { alpha, Box, Stack, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { getIsLiveDataWorking } from "../../../../reducers/selectors/getLiveData";
import { useAppSelector } from "../../../../store/storeHelpers";

export function EmptyListIndicator() {
    const { t } = useTranslation();
    const isLiveDataWorking = useAppSelector(getIsLiveDataWorking);
    return (
        <Box
            sx={(theme) => ({
                position: "absolute",
                inset: 0,
                background: isLiveDataWorking ? null : alpha(theme.palette.background.default, 0.75),
                backdropFilter: "blur(1px)",
                zIndex: isLiveDataWorking ? 0 : 1,
            })}
        >
            <Stack
                direction="column"
                sx={{
                    position: "absolute",
                    top: "50%",
                    left: "50%",
                    transform: "translate(-50%, -100%)",
                    textAlign: "center",
                    alignItems: "center",
                    opacity: 0.25,
                }}
            >
                <CloudOff sx={{ fontSize: "4em" }} />
                <Typography variant="subtitle2" noWrap>
                    {t("variableContext.noData", "data not available yet")}
                </Typography>
            </Stack>
        </Box>
    );
}
