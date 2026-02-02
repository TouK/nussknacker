import { ThreadPrimitive, useThread } from "@assistant-ui/react";
import { ThreadWelcome } from "@assistant-ui/react-ui"; // TODO: "This repo is not actively maintained, these are legacy components and are not up to date."
import { styled } from "@mui/material";
import React from "react";

const StyledThreadSuggestionsContainer = styled(ThreadPrimitive.Empty)(({ theme }) => ({
    position: "fixed",
    bottom: 60,
    left: 0,
    width: "100%",
    zIndex: 1000,
    display: "flex",
    flexWrap: "wrap",
    gap: theme.spacing(0.5),
    paddingTop: theme.spacing(0.5),
    paddingBottom: theme.spacing(0.5),
    paddingLeft: theme.spacing(2),
    paddingRight: theme.spacing(2),
    borderRadius: theme.shape.borderRadius,
    boxShadow: theme.shadows[2],
}));

const StyledSuggestion = styled("div")(({ theme }) => ({
    padding: theme.spacing(0.5),
    borderRadius: theme.shape.borderRadius,
    cursor: "pointer",
    transition: theme.transitions.create(["background-color"], {
        duration: theme.transitions.duration.short,
    }),
    "&:hover": {
        backgroundColor: theme.palette.action.hover,
    },
    whiteSpace: "nowrap",
}));

export const ThreadSuggestions = () => {
    const messages = useThread(({ messages }) => messages);
    const suggestions = useThread(({ suggestions }) => suggestions);

    if (messages.length === 0) {
        return <ThreadWelcome.Message message={"Welcome! How can I help you get started?"} />;
    }

    if (!suggestions.length) return null;
    return (
        <StyledThreadSuggestionsContainer>
            {suggestions.map((suggestion, index) => (
                <StyledSuggestion key={index}>
                    <ThreadWelcome.Suggestion
                        suggestion={{
                            text: suggestion.prompt,
                            prompt: suggestion.prompt,
                        }}
                    />
                </StyledSuggestion>
            ))}
        </StyledThreadSuggestionsContainer>
    );
};
