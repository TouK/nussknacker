import { styled } from "@mui/material";
import React, { PropsWithChildren } from "react";
import { useTranslation } from "react-i18next";

export interface ErrorTemplateProps {
    description?: string;
    message?: string;
}

const ErrorTemplateWrapper = styled("div")`
    text-align: center;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);

    h1 {
        font-size: 96px !important;
    }

    h2 {
        font-size: 64px !important;
    }

    .error-actions {
        margin-top: 15px;
        margin-bottom: 15px;
    }

    .error-actions .btn {
        margin-right: 10px;
    }
`;

const ErrorDetails = styled("div")`
    font-size: 24px;

    .big-blue-button {
        margin: 45px auto;
    }
`;

export function ErrorTemplate({ description = "", message = "", children }: PropsWithChildren<ErrorTemplateProps>): JSX.Element {
    const { t } = useTranslation();

    return (
        <ErrorTemplateWrapper className="center-block">
            <h1>{t("error.title", "Oops!")}</h1>
            <h2>{message}</h2>
            <ErrorDetails>
                <p>{description}</p>
                {children}
            </ErrorDetails>
        </ErrorTemplateWrapper>
    );
}
