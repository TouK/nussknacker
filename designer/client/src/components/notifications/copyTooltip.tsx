import React, { PropsWithChildren, useEffect, useState } from "react";
import copy from "copy-to-clipboard";
import { Button, Tooltip } from "@mui/material";
import { CopyAll, Done } from "@mui/icons-material";

export function useCopyClipboard(): [boolean, (value: string) => void] {
    const [isCopied, setIsCopied] = useState<boolean>();
    const [text, setText] = useState<string>();

    useEffect(() => {
        if (isCopied) {
            const id = setTimeout(() => {
                setIsCopied(false);
            }, 1000);

            return () => {
                clearTimeout(id);
            };
        }
    }, [isCopied, text]);

    return [
        isCopied,
        (value: string) => {
            setText(value);
            setIsCopied(copy(value));
        },
    ];
}

export function CopyTooltip({
    children,
    text,
    title,
}: PropsWithChildren<{
    text: string;
    title: string;
}>): JSX.Element {
    const [isCopied, copy] = useCopyClipboard();
    return (
        <Tooltip
            enterDelay={700}
            leaveDelay={200}
            title={
                <Button
                    size="small"
                    variant="text"
                    color="inherit"
                    startIcon={isCopied ? <Done fontSize="small" /> : <CopyAll fontSize="small" />}
                    onClick={(e) => {
                        copy(text);
                        e.stopPropagation();
                    }}
                >
                    {title}
                </Button>
            }
            componentsProps={{
                popper: {
                    sx: {
                        opacity: 0.8,
                    },
                },
                tooltip: {
                    sx: {
                        bgcolor: (t) => (t.palette.mode === "dark" ? t.palette.common.white : t.palette.common.black),
                        color: (t) => (t.palette.mode === "dark" ? t.palette.common.black : t.palette.common.white),
                    },
                },
                arrow: { sx: { color: (t) => (t.palette.mode === "dark" ? t.palette.common.white : t.palette.common.black) } },
            }}
            placement="bottom-start"
            arrow
        >
            <span>{children}</span>
        </Tooltip>
    );
}
