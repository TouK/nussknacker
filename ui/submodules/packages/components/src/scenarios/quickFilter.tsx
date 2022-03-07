import { useFilterContext } from "../common/filters";
import React, { PropsWithChildren } from "react";
import Paper from "@mui/material/Paper";
import { useTranslation } from "react-i18next";
import { Divider, InputAdornment, Stack } from "@mui/material";
import { InputWithClear } from "../common";
import { Search } from "@mui/icons-material";
import { ScenariosFiltersModel } from "./scenariosFiltersModel";

export function QuickFilter({ children, ...props }: PropsWithChildren<unknown>): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();

    return (
        <Paper elevation={2} sx={{ position: "sticky", top: 0, zIndex: 2 }} {...props}>
            <Paper sx={{ position: "sticky", top: 0, zIndex: 2 }}>
                <Stack
                    component={"form"}
                    noValidate
                    autoComplete="off"
                    direction="row"
                    divider={<Divider orientation="vertical" flexItem />}
                >
                    <InputWithClear
                        placeholder={t("table.filter.QUICK", "Filter...")}
                        fullWidth
                        value={getFilter("NAME") || ""}
                        onChange={setFilter("NAME")}
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
            </Paper>
        </Paper>
    );
}
