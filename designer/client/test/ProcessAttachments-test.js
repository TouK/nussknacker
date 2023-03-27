import React from "react"
import Enzyme, {mount} from "enzyme"
import {ProcessAttachments} from "../components/ProcessAttachments" //import redux-independent component
import Adapter from "@wojtekmaj/enzyme-adapter-react-17"
import configureMockStore from "redux-mock-store"
import thunk from "redux-thunk"
import {Provider} from "react-redux"
import * as selectors from "../reducers/selectors/other"

const mockStore = configureMockStore([thunk])

jest.mock("../containers/theme")
jest.spyOn(selectors, "getCapabilities").mockReturnValue({write: true})

const processAttachment = (id) => ({
  id: `${id}`,
  processId: "proc1",
  processVersionId: 1,
  createDate: "2016-10-10T12:39:44.092",
  user: "TouK",
  fileName: `file ${id}`
})

describe("ProcessAttachments suite", () => {
  it("should render with no problems", () => {
    const store = mockStore({
      graphReducer: {fetchedProcessDetails: {name: "proc1", processVersionId: 1}},
      processActivity: {attachments: [processAttachment(3), processAttachment(2), processAttachment(1)]},
    })

    Enzyme.configure({adapter: new Adapter()})

    const mountedProcessAttachments = mount(
      <Provider store={store}>
        <ProcessAttachments/>
      </Provider>)

    expect(mountedProcessAttachments.find(".download-attachment").length).toBe(3)
  })

})
