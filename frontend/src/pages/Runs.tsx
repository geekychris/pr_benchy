import {
  Table,
  Button,
  Typography,
  Tag,
  Spin,
  Alert,
  Select,
  Space,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { getRuns, getSuites } from '../api/endpoints'
import type { BenchmarkRun } from '../api/types'

const { Title } = Typography

function statusTag(status: string) {
  const map: Record<string, string> = {
    PENDING: 'default',
    RUNNING: 'processing',
    COMPLETED: 'success',
    FAILED: 'error',
    CANCELLED: 'warning',
  }
  return <Tag color={map[status] ?? 'default'}>{status}</Tag>
}

export default function Runs() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const suiteIdParam = searchParams.get('suiteId')

  const { data: runs, isLoading, error } = useQuery({
    queryKey: ['runs', suiteIdParam],
    queryFn: () => getRuns(suiteIdParam ? Number(suiteIdParam) : undefined),
  })

  const { data: suites } = useQuery({ queryKey: ['suites'], queryFn: () => getSuites() })

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: BenchmarkRun) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/runs/${record.id}`)}>
          {name}
        </Button>
      ),
    },
    {
      title: 'Suite',
      key: 'suite',
      render: (_: unknown, record: BenchmarkRun) => record.suite.name,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: statusTag,
    },
    {
      title: 'Bots',
      dataIndex: 'bots',
      key: 'bots',
      render: (bots: BenchmarkRun['bots']) =>
        bots.map((b) => (
          <Tag key={b.id}>{b.name}</Tag>
        )),
    },
    {
      title: 'Concurrency',
      dataIndex: 'concurrency',
      key: 'concurrency',
      width: 110,
    },
    {
      title: 'Started',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (v: string | null) => (v ? dayjs(v).format('MMM D, YYYY HH:mm') : '—'),
    },
    {
      title: 'Completed',
      dataIndex: 'completedAt',
      key: 'completedAt',
      render: (v: string | null) => (v ? dayjs(v).format('MMM D, YYYY HH:mm') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: BenchmarkRun) => (
        <Button size="small" onClick={() => navigate(`/runs/${record.id}`)}>
          View
        </Button>
      ),
    },
  ]

  if (isLoading) return <Spin size="large" />
  if (error) return <Alert type="error" message={(error as Error).message} />

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Benchmark Runs
        </Title>
        <Space>
          <span>Filter by suite:</span>
          <Select
            allowClear
            placeholder="All suites"
            style={{ width: 200 }}
            value={suiteIdParam ? Number(suiteIdParam) : undefined}
            onChange={(val) => {
              if (val) {
                setSearchParams({ suiteId: String(val) })
              } else {
                setSearchParams({})
              }
            }}
          >
            {suites?.map((s) => (
              <Select.Option key={s.id} value={s.id}>
                {s.name}
              </Select.Option>
            ))}
          </Select>
        </Space>
      </div>

      <Table
        dataSource={runs}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 20 }}
      />
    </div>
  )
}
