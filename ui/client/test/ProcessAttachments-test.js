import React from 'react';
import Enzyme, {mount} from 'enzyme';
import {ProcessAttachments} from '../components/ProcessAttachments'; //import redux-independent component
import Adapter from 'enzyme-adapter-react-16';

jest.mock('../containers/theme');

describe("ProcessAttachments suite", () => {
  it("should render with no problems", () => {
    Enzyme.configure({ adapter: new Adapter() });

    //given
    const attachments = [processAttachment(3), processAttachment(2), processAttachment(1)]
    const processId = "proc1"
    const processVersionId = 1

    //when
    const mountedProcessAttachments = mount(
      <ProcessAttachments attachments={attachments} processId={processId} processVersionId={processVersionId} t={key => key}/>
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
