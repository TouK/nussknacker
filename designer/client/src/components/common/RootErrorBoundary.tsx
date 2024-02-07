import React, { PropsWithChildren } from "react";
import { css, styled, Typography } from "@mui/material";
import ProblemOccurredSvg from "./problem-occurred.svg";
import { ErrorBoundary as ErrorBoundaryLibrary, ErrorBoundaryProps } from "react-error-boundary";
import { t } from "i18next";
import { StyledBlueButton } from "../../containers/errors/StyledBlueButton";

type RootErrorPageProps = {
    message: string;
    description: string;
};

const StylesWrapper = styled("div")(
    ({ theme }) => css`
        @font-face {
            font-family: "Space Grotesk";
            font-style: normal;
            font-weight: 300;
            font-display: swap;
            src: url(https://fonts.gstatic.com/s/spacegrotesk/v16/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj62UXsrPMBTTA.woff2) format("woff2");
            unicode-range: U+0102-0103, U+0110-0111, U+0128-0129, U+0168-0169, U+01A0-01A1, U+01AF-01B0, U+0300-0301, U+0303-0304,
                U+0308-0309, U+0323, U+0329, U+1EA0-1EF9, U+20AB;
        }
        /* latin-ext */
        @font-face {
            font-family: "Space Grotesk";
            font-style: normal;
            font-weight: 300;
            font-display: swap;
            src: url(https://fonts.gstatic.com/s/spacegrotesk/v16/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj62UXsqPMBTTA.woff2) format("woff2");
            unicode-range: U+0100-02AF, U+0304, U+0308, U+0329, U+1E00-1E9F, U+1EF2-1EFF, U+2020, U+20A0-20AB, U+20AD-20CF, U+2113,
                U+2C60-2C7F, U+A720-A7FF;
        }
        /* latin */
        @font-face {
            font-family: "Space Grotesk";
            font-style: normal;
            font-weight: 300;
            font-display: swap;
            src: url(https://fonts.gstatic.com/s/spacegrotesk/v16/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj62UXskPMA.woff2) format("woff2");
            unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F,
                U+2074, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
        }

        height: 100%;
        width: 100%;
        padding: 0;
        margin: 0;
        display: grid;
        align-items: center;
        justify-items: center;
        color: ${theme.custom.colors.primaryColor};
        background: ${theme.custom.colors.woodCharcoal};

        .position-wrapper {
            --top-margin: 8vh;
            --bottom-margin: 3vh;
            --side-margin: 5vw;
            position: relative;
            margin: var(--top-margin) var(--side-margin) var(--bottom-margin);
        }

        .text-position {
            container-type: size;
            position: absolute;
            top: 0px;
            left: 0;
            right: 0;
        }

        .text {
            font-family: "Space Grotesk", sans-serif;
            font-weight: 300;
            font-size: 2.1 cqi;
        }

        h1,
        h2 {
            font-weight: inherit;
            margin-block-start: 0;
            margin-block-end: 0;
        }

        h1 {
            font-size: 2em;
        }

        h2 {
            font-size: 1.42em;
        }

        button {
            margin: 45px 0;
        }

        .artwork {
            --top-margin: 8vh;
            --bottom-margin: 3vh;
            --side-margin: 5vw;
            max-width: calc(100vw - 2 * var(--side-margin));
            max-height: calc(100vh - var(--top-margin) - var(--bottom-margin));
            width: 100vmin;
        }
    `,
);

export function RootErrorPage({ message, description, children }: PropsWithChildren<RootErrorPageProps>): JSX.Element {
    return (
        <StylesWrapper>
            <div className="position-wrapper">
                <div className="text-position">
                    <Typography variant={"h1"}>{message}</Typography>
                    <Typography variant={"h2"}>{description}</Typography>
                    {children}
                </div>
                <ProblemOccurredSvg className="artwork" />
            </div>
        </StylesWrapper>
    );
}

export default function RootErrorBoundary({ children }: PropsWithChildren<Partial<ErrorBoundaryProps>>): JSX.Element {
    const fallbackComponent = () => (
        <RootErrorPage
            message={t("error.UnexpectedError.message", "Unexpected error occurred.")}
            description={t(
                "error.UnexpectedError.description",
                "Please refresh the page. If the problem persists, please contact your system administrator.",
            )}
        >
            <StyledBlueButton style={{ border: "none", textAlign: "center" }} onClick={() => window.location.reload()}>
                {t("InitializeError.buttonLabel", "Refresh the page")}
            </StyledBlueButton>
        </RootErrorPage>
    );

    return <ErrorBoundaryLibrary FallbackComponent={fallbackComponent}>{children}</ErrorBoundaryLibrary>;
}
