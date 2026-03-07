import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import {
  DashboardOutlined,
  GithubOutlined,
  AppstoreOutlined,
  RobotOutlined,
  PlayCircleOutlined,
  StarOutlined,
  BarChartOutlined,
  SettingOutlined,
  QuestionCircleOutlined,
  CheckSquareOutlined,
  LineChartOutlined,
  TagsOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'

import Dashboard from './pages/Dashboard'
import Repositories from './pages/Repositories'
import Suites from './pages/Suites'
import SuiteDetail from './pages/SuiteDetail'
import Bots from './pages/Bots'
import Runs from './pages/Runs'
import RunDetail from './pages/RunDetail'
import ReplayPrDetail from './pages/ReplayPrDetail'
import GoldenDataset from './pages/GoldenDataset'
import RunReport from './pages/RunReport'
import Settings from './pages/Settings'
import SetupWizard from './pages/SetupWizard'
import GradingQueue from './pages/GradingQueue'
import TrendReport from './pages/TrendReport'
import IssueTypes from './pages/IssueTypes'
import ScheduledRuns from './pages/ScheduledRuns'

const { Sider, Content } = Layout

const NAV_ITEMS = [
  { key: '/', icon: <DashboardOutlined />, label: <Link to="/">Dashboard</Link> },
  { key: '/repos', icon: <GithubOutlined />, label: <Link to="/repos">Repositories</Link> },
  { key: '/suites', icon: <AppstoreOutlined />, label: <Link to="/suites">Suites</Link> },
  { key: '/bots', icon: <RobotOutlined />, label: <Link to="/bots">Bots</Link> },
  { key: '/runs', icon: <PlayCircleOutlined />, label: <Link to="/runs">Runs</Link> },
  { key: '/golden-dataset', icon: <StarOutlined />, label: <Link to="/golden-dataset">Golden Dataset</Link> },
  { key: '/reports', icon: <BarChartOutlined />, label: <Link to="/runs">Reports</Link> },
  {
    key: 'analysis-group',
    icon: <LineChartOutlined />,
    label: 'Analysis',
    children: [
      { key: '/grading-queue', icon: <CheckSquareOutlined />, label: <Link to="/grading-queue">Grading Queue</Link> },
      { key: '/trend', icon: <LineChartOutlined />, label: <Link to="/trend">Trend Report</Link> },
    ],
  },
  { type: 'divider' as const },
  {
    key: 'config-group',
    icon: <SettingOutlined />,
    label: 'Config',
    children: [
      { key: '/issue-types', icon: <TagsOutlined />, label: <Link to="/issue-types">Issue Types</Link> },
      { key: '/scheduled-runs', icon: <ClockCircleOutlined />, label: <Link to="/scheduled-runs">Scheduled Runs</Link> },
      { key: '/settings', icon: <SettingOutlined />, label: <Link to="/settings">Settings</Link> },
      { key: '/setup', icon: <QuestionCircleOutlined />, label: <Link to="/setup">Setup</Link> },
    ],
  },
]

function AppLayout() {
  const location = useLocation()
  const selectedKey = '/' + location.pathname.split('/')[1]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        theme="dark"
        collapsible
        style={{ position: 'fixed', left: 0, top: 0, bottom: 0, zIndex: 100 }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'white',
            fontSize: 16,
            fontWeight: 'bold',
            padding: '0 16px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
          }}
        >
          PR Bot Benchmark
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={NAV_ITEMS}
        />
      </Sider>
      <Layout style={{ marginLeft: 200 }}>
        <Content style={{ margin: '24px 16px', padding: 24, minHeight: 280 }}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/repos" element={<Repositories />} />
            <Route path="/suites" element={<Suites />} />
            <Route path="/suites/:id" element={<SuiteDetail />} />
            <Route path="/bots" element={<Bots />} />
            <Route path="/runs" element={<Runs />} />
            <Route path="/runs/:id" element={<RunDetail />} />
            <Route path="/replay-prs/:id" element={<ReplayPrDetail />} />
            <Route path="/golden-dataset" element={<GoldenDataset />} />
            <Route path="/reports/:runId" element={<RunReport />} />
            <Route path="/grading-queue" element={<GradingQueue />} />
            <Route path="/trend" element={<TrendReport />} />
            <Route path="/issue-types" element={<IssueTypes />} />
            <Route path="/scheduled-runs" element={<ScheduledRuns />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/setup" element={<SetupWizard />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout />
    </BrowserRouter>
  )
}
