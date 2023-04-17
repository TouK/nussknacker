import React, {useCallback} from "react"
import Dropzone from "react-dropzone"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {ReactComponent as ButtonDownload} from "../assets/img/icons/buttonDownload.svg"
import {ReactComponent as ButtonUpload} from "../assets/img/icons/buttonUpload.svg"
import {NkButton} from "./NkButton"
import HttpService from "../http/HttpService"
import {RootState} from "../reducers"
import Date from "./common/Date"
import {FocusOutline, InputWithFocus} from "./withFocus"
import {getCapabilities} from "../reducers/selectors/other"
import {addAttachment} from "../actions/nk/process"
import {getProcessId, getProcessVersionId} from "../reducers/selectors/graph"
import {Attachment} from "../reducers/processActivity"

function AttachmentEl({data}: { data: Attachment }) {
  return (
    <li className={"attachment-section"}>
      <div className="download-attachment">
        <NkButton
          className="download-button"
          onClick={() => HttpService.downloadAttachment(data.processId, data.processVersionId, data.id, data.fileName)}
        >
          <ButtonDownload/>
        </NkButton>
      </div>
      <div className={"attachment-details"}>
        <div className="header">
          <Date date={data.createDate}/>
          <span>{` | v${data.processVersionId} | ${data.user}`}</span>
        </div>
        <p> {data.fileName} </p>
      </div>
    </li>
  )
}

function AddAttachment() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const processId = useSelector(getProcessId)
  const processVersionId = useSelector(getProcessVersionId)
  const addFiles = useCallback(
    (files: File[]) => files.forEach((file) => dispatch(addAttachment(processId, processVersionId, file))),
    [dispatch, processId, processVersionId]
  )

  return (
    <FocusOutline className="add-attachments">
      <Dropzone onDrop={addFiles}>
        {({getRootProps, getInputProps}) => (
          <FocusOutline className="attachments-container" {...getRootProps()}>
            <FocusOutline
              className={"attachment-drop-zone attachment-button"}
            >
              <ButtonUpload/>
            </FocusOutline>
            <div className="attachment-button-text">
              <span>{t("attachments.buttonText", "drop or choose a file")}</span>
            </div>
            <InputWithFocus {...getInputProps()}/>
          </FocusOutline>
        )}
      </Dropzone>
    </FocusOutline>
  )
}

export function ProcessAttachments() {
  const {write} = useSelector(getCapabilities)
  const attachments = useSelector((s: RootState) => s.processActivity.attachments)

  return (
    <div className="process-attachments">
      <ul className="process-attachments-list">
        {attachments.map((a) => <AttachmentEl key={a.id} data={a}/>)}
      </ul>
      {write && <AddAttachment/>}
    </div>
  )
}

export default ProcessAttachments
