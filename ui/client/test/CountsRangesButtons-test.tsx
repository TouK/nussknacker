import Adapter from "@wojtekmaj/enzyme-adapter-react-17"
import Enzyme, {mount} from "enzyme"
import moment from "moment"
import React from "react"
import {Simulate} from "react-dom/test-utils"
import {CountsRangesButtons} from "../components/modals/CalculateCounts/CountsRangesButtons"

jest.mock("../containers/theme")

describe("CountsRangesButtons tests", () => {
  Enzyme.configure({adapter: new Adapter()})
  const m = moment("2001-10-19T23:00:00.000Z")
  const range1 = {name: "range1", from: () => m.clone(), to: () => m.clone().add(1, "hour")}
  const range2 = {name: "range2", from: () => m.clone().add(1, "day"), to: () => m.clone().add(2, "days")}
  const range3 = {name: "range3", from: () => m.clone().add(1, "week"), to: () => m.clone().add(2, "weeks")}
  const ranges = [range1, range2, range3]
  const changeFn = jest.fn()

  beforeEach(() => {
    changeFn.mockReset()
  })

  it("should render buttons", () => {
    const wrapper = mount(<CountsRangesButtons ranges={ranges} onChange={changeFn}/>)
    expect(wrapper.html()).toMatchSnapshot()
  })

  it("should handle click", () => {
    const wrapper = mount(<CountsRangesButtons ranges={ranges} onChange={changeFn}/>)
    wrapper.find("button").first().simulate("click")
    expect(changeFn).toHaveBeenCalledTimes(1)
    expect(changeFn).toHaveBeenCalledWith([range1.from(), range1.to()])
  })

  it("should collapse buttons", () => {
    const wrapper = mount(
      <CountsRangesButtons ranges={ranges} onChange={changeFn} limit={1}/>,
      {attachTo: document.body},
    )

    const buttons = wrapper.find("button")
    expect(buttons).toHaveLength(2)
    const options = document.getElementsByClassName("nodeValueSelect__option")
    expect(options).toHaveLength(0)
    buttons.last().simulate("click")
    const options2 = document.getElementsByClassName("nodeValueSelect__option")
    expect(options2).toHaveLength(2)
    Simulate.click(options2.item(1))
    expect(changeFn).toHaveBeenCalledTimes(1)
    expect(changeFn).toHaveBeenCalledWith([range3.from(), range3.to()])
  })

  it("should hide expand button when not needed", () => {
    const wrapper = mount(
      <CountsRangesButtons ranges={ranges} onChange={changeFn} limit={10}/>,
    )

    const buttons = wrapper.find("button")
    expect(buttons).toHaveLength(3)
    buttons.last().simulate("click")
    expect(changeFn).toHaveBeenCalledTimes(1)
    expect(changeFn).toHaveBeenCalledWith([range3.from(), range3.to()])
  })
})
