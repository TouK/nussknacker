import { useComposerRuntime, useThread } from "@assistant-ui/react";
import { Stack, styled } from "@mui/material";
import React, { useCallback, useEffect, useRef, useState } from "react";

import { addListenerTyped, useAppDispatch } from "../../../store/storeHelpers";
import { delay } from "../../../utils";
import { LoadingButton } from "../../../windowManager/LoadingButton";
import { TextAreaNode } from "../../FormElements";
import { nodeInput } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";

const StyledTextArea = styled(TextAreaNode)({
    fontSize: 14,
    paddingBlock: "0.5em",
    overflowX: "hidden",
    overflowY: "auto",
    maxHeight: "30vh",
    resize: "none",
});

const adjustInputHeight = (textarea: HTMLTextAreaElement) => {
    if (!textarea) return;
    textarea.style.height = "auto";
    const { paddingBottom, paddingTop, maxHeight } = getComputedStyle(textarea);
    let height = textarea.getBoundingClientRect().height;
    const paddingY = Math.max(0, parseFloat(paddingTop)) + Math.max(0, parseFloat(paddingBottom));
    const lh = height - paddingY;
    while (textarea.scrollHeight > height && height < (parseFloat(maxHeight) || window.innerHeight * 0.5)) {
        textarea.style.height = `${height + lh}px`;
        height = height + lh;
    }
};

export const Composer = () => {
    const { send, setText, cancel } = useComposerRuntime();
    const dispatch = useAppDispatch();

    const [message, setMessage] = useState("");
    const textAreaRef = useRef<HTMLTextAreaElement>(null);
    const { isRunning } = useThread();
    const isSendDisabled = !message || isRunning;

    const handleChange = useCallback(
        (event: React.ChangeEvent<HTMLTextAreaElement>) => {
            const newValue = event.target.value;
            setMessage(newValue);
            setText(newValue);
            adjustInputHeight(textAreaRef.current);
        },
        [setText],
    );

    const handleSend = useCallback(() => {
        if (isSendDisabled) {
            return;
        }

        send();
        setMessage("");
        adjustInputHeight(textAreaRef.current);
    }, [isSendDisabled, send]);

    const handleCancel = useCallback(() => {
        cancel();
    }, [cancel]);

    const handleKeyDownOnInput = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            handleSend();
        }
    };

    useEffect(() => {
        textAreaRef.current?.focus();
        return dispatch(
            addListenerTyped("ASSISTANT_FOCUS", async () => {
                await delay();
                textAreaRef.current?.focus();
            }),
        );
    }, [dispatch]);

    useEffect(() => {
        adjustInputHeight(textAreaRef.current);
    }, []);

    return (
        <Stack
            component="footer"
            spacing={1}
            direction="row"
            sx={{
                paddingX: 2,
                paddingY: 1.5,
                alignItems: "flex-start",
                justifyContent: "space-between",
            }}
            data-no-focus-lock
        >
            <Stack sx={{ flex: 1, alignSelf: "center" }}>
                <StyledTextArea
                    ref={textAreaRef}
                    autoComplete="off"
                    name="message"
                    placeholder="Message AI Assistant, e.g. what is a scenario?"
                    value={message}
                    className={nodeInput}
                    onChange={handleChange}
                    onKeyDown={handleKeyDownOnInput}
                    rows={1}
                />
            </Stack>
            <LoadingButton
                title={isRunning ? "Cancel" : "Send"}
                action={isRunning ? handleCancel : handleSend}
                disabled={isRunning ? false : !message}
            />
        </Stack>
    );
};
