import classNames from "classnames"
import * as  queryString from "query-string"
import React from "react"
import {withRouter} from "react-router-dom"
import {Tab, TabList, TabPanel, Tabs} from "react-tabs"
import "react-tabs/style/react-tabs.css"
import Services from "./admin/Services"
import {Page} from "./Page"
import {compose} from "redux"
import style from "./adminPage.styl"
import {AdminPagePath} from "./paths"

export class AdminPage extends React.Component {
  tabs = [
    {key: Services.key, title: Services.header, component: <Services/>},
  ]

  constructor(props) {
    super(props)

    const query = queryString.parse(window.location.search, {parseNumbers: true})

    this.state = {
      selectedTab: query.tab || 0,
    }
  }

  onTabChange = (index) => {
    const params = {tab: index}
    this.props.history.replace({search: queryString.stringify(params)})
    this.setState(params)
  }

  render() {
    return (
      <Page>
        <Tabs defaultIndex={this.state.selectedTab} onSelect={this.onTabChange} className={classNames("react-tabs", style.tabs)}>
          <TabList>
            {
              this.tabs.map(tab => {
                return (
                  <Tab key={tab.key}>{tab.title}</Tab>
                )
              })
            }
          </TabList>
          {
            this.tabs.map(tab => {
              return (
                <TabPanel key={tab.key} className={classNames("react-tabs__tab-panel", style.tab)}>{tab.component}</TabPanel>
              )
            })
          }
        </Tabs>
      </Page>
    )
  }
}

const enhance = compose(
  withRouter,
)

export const NkAdminPage = enhance(AdminPage)
