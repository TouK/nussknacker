import { InputWithClear, useFilterContext } from "../../common";
import React, { PropsWithChildren } from "react";
import Paper from "@mui/material/Paper";
import { useTranslation } from "react-i18next";
import { Divider, Grow, InputAdornment, LinearProgress, Stack } from "@mui/material";
import { Search } from "@mui/icons-material";

export function QuickFilter<F extends Record<string, any>>({
    children,
    isLoading,
    filter,
    ...props
}: PropsWithChildren<{ filter: keyof F; isLoading?: boolean }>): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilterImmediately } = useFilterContext<F>();

    return (
        <Paper elevation={2} sx={{ position: "sticky", top: -1, zIndex: 2 }} {...props}>
            <Stack component={"form"} noValidate autoComplete="off" direction="row" divider={<Divider orientation="vertical" flexItem />}>
                <InputWithClear
                    placeholder={t("table.filter.QUICK", "Search...")}
                    fullWidth
                    value={getFilter(filter) || ""}
                    onChange={setFilterImmediately(filter)}
                    sx={{
                        ".MuiOutlinedInput-notchedOutline": {
                            borderStartEndRadius: 0,
                            borderEndEndRadius: 0,
                            borderColor: "transparent",
                            legend: {
                                width: 0,
                            },
                        },
                    }}
                    startAdornment={
                        <InputAdornment position="start">
                            <Search sx={{ marginTop: "3px", opacity: 0.5 }} />
                        </InputAdornment>
                    }
                />
                {children}
            </Stack>
            <Grow in={isLoading} unmountOnExit>
                <LinearProgress
                    sx={{
                        position: "absolute",
                        bottom: 0,
                        left: 0,
                        right: 0,
                        borderBottomLeftRadius: (t) => t.shape.borderRadius,
                        borderBottomRightRadius: (t) => t.shape.borderRadius,
                    }}
                />
            </Grow>
        </Paper>
    );
}
