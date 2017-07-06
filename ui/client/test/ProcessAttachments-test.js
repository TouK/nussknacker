import React from 'react';
import { shallow, mount, render } from 'enzyme';
import { ProcessAttachments_ } from '../components/ProcessAttachments'; //import redux-independent component

describe("ProcessAttachments suite", () => {
  it("should render with no problems", () => {
    //given
    const attachments = [processAttachment(3), processAttachment(2), processAttachment(1)]
    const processId = "proc1"
    const processVersionId = 1

    //when
    const mountedProcessAttachments = mount(
      <ProcessAttachments_ attachments={attachments} processId={processId} processVersionId={processVersionId}/>
    )
    //then
    expect(mountedProcessAttachments.find('.download-attachment').length).toBe(3)
  })

  const processAttachment = (id) => {
    return {
      id: `${id}`,
      processId: "proc1",
      processVersionId: 1,
      createDate: "2016-10-10T12:39:44.092",
      user: "TouK",
      fileName: `file ${id}`
    }
  }

});
