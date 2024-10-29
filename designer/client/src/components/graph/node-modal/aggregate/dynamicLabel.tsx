import { Box, styled } from "@mui/material";

type DynamicLabelProps = {
    label: string;
    hovered: boolean;
};

export const DynamicLabel = styled(Box, {
    shouldForwardProp: (propName: string) => !["text", "hovered"].includes(propName),
})<DynamicLabelProps>(({ theme, label, hovered }) => ({
    "&::before": {
        ...theme.typography.overline,
        color: theme.palette.text.disabled,

        position: "absolute",
        bottom: "100%",
        marginBottom: theme.spacing(0.75),

        "[data-testid^='draggable']:first-of-type &": {
            content: hovered ? "unset" : `'${label}'`,
        },

        "[data-testid^='draggable'][style*='fixed'] &": {
            content: hovered ? `'${label}'` : "unset",
        },
    },
}));
