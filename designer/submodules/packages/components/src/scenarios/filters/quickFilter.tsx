import { Search } from "@mui/icons-material";
import { Divider, Grow, InputAdornment, LinearProgress, Stack } from "@mui/material";
import Paper from "@mui/material/Paper";
import type { FormEventHandler, PropsWithChildren } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import { InputWithClear } from "../../common/forms/inputWithClear";
import { useScenariosFilterContext } from "./common/useScenariosFilterContext";

const preventSubmit: FormEventHandler<HTMLFormElement> = (e) => e.preventDefault();

export function QuickFilter<F extends Record<string, any>>({
    children,
    isLoading,
    filter,
    ...props
}: PropsWithChildren<{
    filter: keyof F;
    isLoading?: boolean;
}>): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useScenariosFilterContext<F>();

    return (
        <Paper elevation={2} sx={{ position: "sticky", top: -1, zIndex: 2 }} {...props}>
            <Stack
                component={"form"}
                noValidate
                onSubmit={preventSubmit}
                autoComplete="off"
                direction="row"
                divider={<Divider orientation="vertical" flexItem />}
            >
                <InputWithClear
                    placeholder={t("table.filter.QUICK", "Search...")}
                    fullWidth
                    value={getFilter(filter) || ""}
                    onChange={setFilter(filter)}
                    sx={{
                        flex: 1,
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
                        <InputAdornment sx={(theme) => ({ color: theme.palette.text.secondary })} position="start">
                            <Search sx={{ marginTop: "3px" }} />
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
