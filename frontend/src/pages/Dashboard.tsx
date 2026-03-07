import { useQuery } from '@tanstack/react-query'
import { Row, Col, Card, Statistic, Table, Tag, Typography, Button, Spin, Alert, Empty } from 'antd'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { getRepos, getSuites, getRuns } from '../api/endpoints'
import type { BenchmarkRun } from '../api/types'

const { Title } = Typography

function statusBadge(status: string) {
  const map: Record<string, string> = {
    PENDING: 'default',
    RUNNING: 'processing',
    COMPLETED: 'success',
    FAILED: 'error',
    CANCELLED: 'warning',
  }
  return <Tag color={map[status] ?? 'default'}>{status}</Tag>
}

export default function Dashboard() {
  const navigate = useNavigate()

  const { data: repos, isLoading: reposLoading } = useQuery({
    queryKey: ['repos'],
    queryFn: getRepos,
  })

  const { data: suites, isLoading: suitesLoading } = useQuery({
    queryKey: ['suites'],
    queryFn: () => getSuites(),
  })

  const { data: runs, isLoading: runsLoading, error: runsError } = useQuery({
    queryKey: ['runs'],
    queryFn: () => getRuns(),
  })

  const activeRuns = runs?.filter((r) => r.status === 'RUNNING').length ?? 0

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: BenchmarkRun) => (
        <Button type="link" onClick={() => navigate(`/runs/${record.id}`)} style={{ padding: 0 }}>
          {name}
        </Button>
      ),
    },
    {
      title: 'Suite',
      dataIndex: ['suite', 'name'],
      key: 'suite',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: statusBadge,
    },
    {
      title: 'Bots',
      dataIndex: 'bots',
      key: 'bots',
      render: (bots: BenchmarkRun['bots']) => bots.map((b) => b.name).join(', '),
    },
    {
      title: 'Started',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (v: string | null) => (v ? dayjs(v).format('MMM D, YYYY HH:mm') : '-'),
    },
    {
      title: 'Completed',
      dataIndex: 'completedAt',
      key: 'completedAt',
      render: (v: string | null) => (v ? dayjs(v).format('MMM D, YYYY HH:mm') : '-'),
    },
  ]

  const isLoading = reposLoading || suitesLoading || runsLoading

  if (isLoading) return <Spin size="large" />
  if (runsError) return <Alert type="error" message={(runsError as Error).message} />

  const recentRuns = [...(runs ?? [])].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  ).slice(0, 10)

  return (
    <div>
      <Title level={2}>Dashboard</Title>
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title="Total Repositories" value={repos?.length ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="Total Suites" value={suites?.length ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="Total Runs" value={runs?.length ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Active Runs"
              value={activeRuns}
              valueStyle={{ color: activeRuns > 0 ? '#1677ff' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="Recent Runs"
        extra={
          <Button type="primary" onClick={() => navigate('/runs')}>
            View All Runs
          </Button>
        }
      >
        {recentRuns.length === 0 ? (
          <Empty
            description="No runs yet"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Button type="primary" onClick={() => navigate('/suites')}>
              Create a Suite to Get Started
            </Button>
          </Empty>
        ) : (
          <Table
            dataSource={recentRuns}
            columns={columns}
            rowKey="id"
            pagination={false}
            size="small"
          />
        )}
      </Card>
    </div>
  )
}
