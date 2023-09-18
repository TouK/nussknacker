import React from "react";
import DownloadIcon from "@mui/icons-material/Download";
import { Attachment } from "../../reducers/processActivity";
import HttpService from "../../http/HttpService";
import Date from "../common/Date";
import { AttachmentDetails, DownloadAttachment, DownloadButton, AttachHeader } from "./StyledAttach";

export function AttachmentEl({ data }: { data: Attachment }) {
    return (
        <li style={{ display: "flex" }}>
            <DownloadAttachment className="download-attachment">
                <DownloadButton
                    onClick={() => HttpService.downloadAttachment(data.processId, data.processVersionId, data.id, data.fileName)}
                >
                    <DownloadIcon sx={{ width: 13, height: 13 }} />
                </DownloadButton>
            </DownloadAttachment>
            <AttachmentDetails>
                <AttachHeader>
                    <Date date={data.createDate} />
                    <span>{` | v${data.processVersionId} | ${data.user}`}</span>
                </AttachHeader>
                <p> {data.fileName} </p>
            </AttachmentDetails>
        </li>
    );
}
