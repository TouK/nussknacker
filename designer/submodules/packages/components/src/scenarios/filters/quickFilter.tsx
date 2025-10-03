import { Search } from "@mui/icons-material";
import { Box, Divider, Grow, InputAdornment, LinearProgress, Stack } from "@mui/material";
import Paper from "@mui/material/Paper";
import { alpha } from "@mui/material/styles";
import type { FormEventHandler, PropsWithChildren } from "react";
import React, { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDocumentEventListener } from "rooks";

import { InputWithClear } from "../../common/forms/inputWithClear";
import { useScenariosFilterContext } from "./common/useScenariosFilterContext";
import Scale from "./scale";
import { useFocusWithinState } from "./useFocusWithinState";
import { useSyncedState } from "./useSyncedState";
import { useWrappedStack } from "./wrappedStack";

const preventSubmit: FormEventHandler<HTMLFormElement> = (e) => e.preventDefault();

function useFilterStateDebounced<F extends Record<string, any>>(filter: keyof F) {
    const { getFilter, setFilter } = useScenariosFilterContext<F>();
    const storedValue = useMemo<string>(() => getFilter(filter) || "", [filter, getFilter]);
    const setStoredValue = setFilter(filter);
    return useSyncedState([storedValue, setStoredValue]);
}

function isSimpleChar(event: KeyboardEvent) {
    const hasModifiers = event.shiftKey || event.altKey || event.ctrlKey || event.metaKey;
    const simpleCharacters = event.key.length === 1 && /^[a-zA-Z0-9]$/.test(event.key);
    return simpleCharacters && !hasModifiers;
}

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
    const [value, setValue] = useFilterStateDebounced<F>(filter);
    const { results } = useScenariosFilterContext<F>();

    const inputRef = useRef<HTMLInputElement>();
    const inputWrapperRef = useRef<HTMLElement>();
    const inputHeight = inputWrapperRef.current?.getBoundingClientRect().height;

    const [expansionSize, setExpansionSize] = useState(0);
    const { focused, focusWithinProps } = useFocusWithinState();

    const { primaryLine, secondaryLine } = useWrappedStack(children, (box) => setExpansionSize(box?.width || 0));

    const inputWidth = useMemo(() => {
        const adjustToQuery = `${(value || "").length + 15}ch)`;
        const adjustToSpace = `calc(100% + ${expansionSize}px)`;
        return focused ? `min(max(${adjustToSpace}, ${adjustToQuery}, max(${adjustToSpace}, 80vw))` : adjustToSpace;
    }, [expansionSize, focused, value]);

    useDocumentEventListener("keydown", (event: KeyboardEvent) => {
        if (isSimpleChar(event)) inputRef.current?.focus();
    });

    return (
        <Paper
            elevation={2}
            sx={{
                position: "sticky",
                top: 0,
                zIndex: 2,
                overflow: "hidden",
            }}
            {...props}
        >
            <Stack component={"form"} noValidate onSubmit={preventSubmit} autoComplete="off" direction="row">
                <Box sx={{ flex: 1, position: "relative", minWidth: "max(20%, 200px)" }} style={{ height: inputHeight }}>
                    <Box
                        ref={inputWrapperRef}
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
                            inputRef={inputRef}
                            placeholder={t("table.filter.QUICK", "Search...")}
                            fullWidth
                            value={value}
                            onChange={setValue}
                            error={value && results && results.length < 1}
                            sx={(theme) => ({
                                backgroundColor: alpha(theme.palette.background.paper, 0.75),
                                backdropFilter: "blur(25px)",
                                borderStartEndRadius: 0,
                                borderEndEndRadius: 0,
                                borderEndStartRadius: secondaryLine ? 0 : null,
                                ".MuiOutlinedInput-notchedOutline": {
                                    borderColor: "transparent",
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
                    })}
                >
                    <Scale zoom={0.8} origin="right">
                        {secondaryLine}
                    </Scale>
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
