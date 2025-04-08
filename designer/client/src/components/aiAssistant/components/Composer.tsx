import { useComposerRuntime, useThread } from "@assistant-ui/react";
import { styled } from "@mui/material";
import React, { useState, useEffect, useCallback, useRef } from "react";

import { LoadingButton } from "../../../windowManager/LoadingButton";
import { TextAreaNode } from "../../FormElements";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeInput, nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { UseScrollToBottom } from "./useScrollToBottom";

const StyledRoot = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "row",
    alignItems: "flex-end",
    margin: theme.spacing(0, 2),
    "& .MuiLoadingButton-root": {
        marginRight: 0,
    },
    position: "relative", // ensure proper positioning
}));

const StyledTextArea = styled(TextAreaNode)(({ theme }) => ({
    marginBottom: theme.spacing(1.125),
}));

const resetInputHeight = (textarea: HTMLTextAreaElement) => {
    if (textarea) {
        // Resetting styles
        textarea.style.marginTop = "0";
        textarea.style.height = "auto";
    }
};

const adjustInputHeight = (textarea: HTMLTextAreaElement) => {
    if (textarea) {
        resetInputHeight(textarea);

        // Define modal maximum height (50vh)
        const modalMaxHeight = window.innerHeight * 0.5;

        // Calculate new height and cap it if needed
        let newHeight = textarea.scrollHeight;
        if (newHeight > modalMaxHeight) {
            newHeight = modalMaxHeight;
            textarea.style.overflowY = "auto";
        } else {
            textarea.style.overflowY = "hidden";
        }

        textarea.style.height = `${newHeight}px`;

        // Anchor the bottom by shifting the extra height upward
        const baseHeight = 38; // adjust base height as needed
        const diff = newHeight - baseHeight;
        if (newHeight < modalMaxHeight) {
            textarea.style.marginTop = `-${diff}px`;
        }
    }
};

export const Composer = () => {
    const { send, setText, cancel } = useComposerRuntime();
    const { scrollToBottom, provideBottomSpacer } = UseScrollToBottom();

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

        provideBottomSpacer();
        scrollToBottom();
        send();
        setMessage("");
        resetInputHeight(textAreaRef.current);
    }, [isSendDisabled, provideBottomSpacer, scrollToBottom, send]);

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
        scrollToBottom();
    }, [scrollToBottom]);

    return (
        <StyledRoot data-no-focus-lock>
            <NodeTable sx={{ margin: 0, marginRight: "16px", width: "100%" }}>
                <div className={nodeValue}>
                    <StyledTextArea
                        autoFocus
                        onFocus={(e) => {
                            // Disable nk-windows focus event which move second window when opened, to the background on input clicks
                            e.stopPropagation();
                        }}
                        ref={textAreaRef}
                        autoComplete="off"
                        name="message"
                        placeholder="Message AI Assistant, e.g. what is a scenario?"
                        value={message}
                        className={nodeInput}
                        onChange={handleChange}
                        onKeyDown={handleKeyDownOnInput}
                        rows={1}
                        style={{ resize: "none", overflow: "hidden" }}
                    />
                </div>
            </NodeTable>
            {isRunning ? (
                <LoadingButton title="Cancel" action={handleCancel} />
            ) : (
                <LoadingButton disabled={!message} title="Send" action={handleSend} />
            )}
        </StyledRoot>
    );
};
