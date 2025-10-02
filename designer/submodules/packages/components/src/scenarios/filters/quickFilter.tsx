import { Search } from "@mui/icons-material";
import { Box, Divider, Grow, InputAdornment, LinearProgress, Stack } from "@mui/material";
import Paper from "@mui/material/Paper";
import { alpha } from "@mui/material/styles";
import type { FormEventHandler, PropsWithChildren } from "react";
import React, { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { InputWithClear } from "../../common/forms/inputWithClear";
import { useScenariosFilterContext } from "./common/useScenariosFilterContext";
import { useFocusWithinState } from "./useFocusWithinState";
import { useWrappedStack } from "./wrappedStack";

const preventSubmit: FormEventHandler<HTMLFormElement> = (e) => e.preventDefault();

// eslint-disable-next-line @typescript-eslint/no-explicit-any
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

    const inputRef = useRef<HTMLElement>();
    const inputHeight = inputRef.current?.getBoundingClientRect().height;

    const [expansionSize, setExpansionSize] = useState(0);
    const { focused, focusWithinProps } = useFocusWithinState();

    const value = useMemo<string>(() => getFilter(filter) || "", [filter, getFilter]);

    const { primaryLine, secondaryLine } = useWrappedStack(children, (box) => setExpansionSize(box?.width || 0));

    const inputWidth = useMemo(
        () =>
            focused
                ? `min(max(360px, calc(100% + ${expansionSize}px + 5ch), ${value.length + 15}ch), 80vw)`
                : `calc(100% + ${expansionSize}px)`,
        [expansionSize, focused, value.length],
    );
    return (
        <Paper elevation={2} sx={{ position: "sticky", top: -1, zIndex: 2 }} {...props}>
            <Stack component={"form"} noValidate onSubmit={preventSubmit} autoComplete="off" direction="row">
                <Box sx={{ flex: 1, position: "relative", minWidth: 128 }} style={{ height: inputHeight }}>
                    <Box
                        ref={inputRef}
                        sx={(theme) => ({
                            display: "flex",
                            position: "absolute",
                            left: 0,
                            zIndex: theme.zIndex.appBar,
                            transition: theme.transitions.create("width"),
                        })}
                        style={{ width: inputWidth }}
                        {...focusWithinProps}
                    >
                        <InputWithClear
                            placeholder={t("table.filter.QUICK", "Search...")}
                            fullWidth
                            value={value}
                            onChange={setFilter(filter)}
                            sx={(theme) => ({
                                backgroundColor: alpha(theme.palette.background.paper, 0.75),
                                backdropFilter: "blur(25px)",
                                borderStartEndRadius: 0,
                                borderEndEndRadius: 0,
                                borderEndStartRadius: secondaryLine ? 0 : null,
                                ".MuiOutlinedInput-notchedOutline": {
                                    borderStartEndRadius: 0,
                                    borderEndEndRadius: 0,
                                    borderEndStartRadius: secondaryLine ? 0 : null,
                                    borderColor: "transparent",
                                    legend: {
                                        width: 0,
                                    },
                                },
                            })}
                            startAdornment={
                                <InputAdornment sx={(theme) => ({ color: theme.palette.text.secondary })} position="start">
                                    <Search sx={{ marginTop: "3px" }} />
                                </InputAdornment>
                            }
                        />
                        <Divider orientation="vertical" flexItem />
                    </Box>
                </Box>
                {primaryLine}
            </Stack>
            {secondaryLine ? (
                <Box
                    sx={(theme) => ({
                        borderTop: "2px solid",
                        borderTopColor: theme.palette.divider,
                        backgroundColor: theme.palette.background.paper,
                        borderEndStartRadius: theme.shape.borderRadius,
                        borderEndEndRadius: theme.shape.borderRadius,
                        zoom: 0.8,
                        position: "relative",
                        zIndex: theme.zIndex.appBar + 6,
                    })}
                >
                    {secondaryLine}
                </Box>
            ) : null}
            <Grow in={isLoading} unmountOnExit>
                <LinearProgress
                    sx={(theme) => ({
                        position: "absolute",
                        bottom: 0,
                        left: 0,
                        right: 0,
                        zIndex: theme.zIndex.appBar + 2,
                        borderBottomLeftRadius: (t) => t.shape.borderRadius,
                        borderBottomRightRadius: (t) => t.shape.borderRadius,
                    })}
                />
            </Grow>
        </Paper>
    );
}
