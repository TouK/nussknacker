import { Stack, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

export function Stats({ current = 0, all = 0, isLoading }: { current: number; all: number; isLoading?: boolean }): JSX.Element {
    const { t } = useTranslation();

    return (
        <Stack direction="row" mx={2} spacing={1} justifyContent="center">
            <Typography
                variant="button"
                sx={{
                    color: (t) => t.palette.getContrastText(t.palette.background.default),
                    opacity: 0.6,
                }}
            >
                {isLoading ? (
                    <>{t("list.rows.loading", "loading...")}</>
                ) : all < 1 ? (
                    <>{t("list.rows.empty", "the list is empty")}</>
                ) : current < 1 ? (
                    <>{t("list.rows.noMatch", "none of the {{count}} rows match the filters", { count: all })}</>
                ) : all !== current ? (
                    <>
                        {t("list.rows.match", "{{match}} of the {{count}} rows match the filters", {
                            count: all,
                            match: current,
                        })}
                    </>
                ) : (
                    <>
                        {t("list.rows.everything", "every of the {{count}} rows match the filters", {
                            count: all,
                            match: current,
                        })}
                    </>
                )}
            </Typography>
        </Stack>
    );
}
