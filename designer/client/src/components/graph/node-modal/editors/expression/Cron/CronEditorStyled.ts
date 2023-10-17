import { styled } from "@mui/material";

export const CronEditorStyled = styled("div")`
    width: 70%;

    * {
        color: #ccc !important;
    }

    .cron_builder_bordering {
        border-radius: none;
        border: none;
        padding: 8px 0 0 0;
        text-align: left;
    }

    .container-fluid {
        padding: 0;
    }

    .cron_builder {
        width: 100%;
        background-color: #333;
        outline: 1px solid rgba(255, 255, 255, 0.075);
        border: none;
    }

    .well {
        display: flex;
        align-items: center;
        background-color: #393939 !important;
        border: 1px solid #666 !important;
        margin-bottom: 8px !important;
        border-radius: 0 !important;
        padding: 12px !important;
    }

    .row {
        margin-left: auto !important;
        margin-right: auto !important;
    }

    .nav-tabs {
        border-bottom: 1px solid #666 !important;
    }

    .cron_builder_bordering input {
        border-radius: 0;
        background: #2d2d2d;
    }

    .cron_builder_bordering input[type="radio"] {
        margin-top: 2px;
        vertical-align: text-top;
    }

    .cron_builder_bordering input[type="number"] {
        height: 26px;
        border: 1px solid #666;
        width: 12% !important;
    }

    .span6 {
        align-self: flex-start;
        padding: 0;
    }

    input[type="radio"],
    input[type="checkbox"] {
        vertical-align: bottom;
        margin: 8px 0 0 0;
        transform: scale(1.2);
        background-color: #334;
        -moz-appearance: none;
    }

    .nav-tabs > li > a {
        border-radius: 0;
        border-right: 1px solid #666;
        border-left: 1px solid #666;
        border-top: 1px solid #666;
        border-bottom: 0;
        color: #337ab7 !important;
    }

    .nav-tabs > li.active > a,
    .nav-tabs > li.active > a:hover,
    .nav-tabs > li.active > a:focus,
    .nav-tabs > li > a:hover,
    .cron_builder .nav-tabs > li.active > a,
    .nav-tabs > li.active > a:hover,
    .nav-tabs > li.active > a:focus {
        background: #4a4a4a;
        border-right: 1px solid #666;
        border-left: 1px solid #666;
        border-top: 1px solid #666;
        border-bottom: 0;
        color: #999;
    }

    .cron_builder .nav-tabs > li.active > a {
        color: #ccc;
        border-bottom: 1px solid #666;
    }

    .cron-builder-bg {
        background-color: #333 !important;
        margin-top: 8px;
    }

    .minutes,
    .hours {
        background: #2d2d2d;
        border-radius: 0;
        border: 1px solid #666;
        margin-left: 8px;
    }

    .col-md-offset-2 {
        margin-left: 0 !important;
    }

    .col-md-6 {
        width: 100% !important;
        padding-left: 0 !important;
    }
`;
