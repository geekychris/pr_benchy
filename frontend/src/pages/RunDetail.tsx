import { useEffect } from 'react'
import {
  Typography,
  Card,
  Row,
  Col,
  Tag,
  Button,
  Table,
  Progress,
  Spin,
  Alert,
  Space,
  Descriptions,
  Tabs,
  Popconfirm,
  message,
  Statistic,
} from 'antd'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  getRun,
  getRunProgress,
  getReplayPrs,
  cancelRun,
  cleanupRun,
  exportRunCsv,
  exportRunSarif,
  getBotSnapshots,
} from '../api/endpoints'
import type { ReplayPr, BotSnapshot } from '../api/types'

const { Title, Text } = Typography

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

function replayStatusTag(status: string) {
  if (status === 'COMPLETED') return <Tag color="success">{status}</Tag>
  if (status === 'FAILED') return <Tag color="error">{status}</Tag>
  if (status === 'RUNNING' || status === 'WAITING') return <Tag color="processing">{status}</Tag>
  return <Tag>{status}</Tag>
}

export default function RunDetail() {
  const { id } = useParams<{ id: string }>()
  const runId = Number(id)
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: run, isLoading, error } = useQuery({
    queryKey: ['run', runId],
    queryFn: () => getRun(runId),
  })

  const { data: progress } = useQuery({
    queryKey: ['run-progress', runId],
    queryFn: () => getRunProgress(runId),
    refetchInterval: run?.status === 'RUNNING' ? 10_000 : false,
  })

  const { data: replayPrs } = useQuery({
    queryKey: ['replay-prs', runId],
    queryFn: () => getReplayPrs(runId),
    refetchInterval: run?.status === 'RUNNING' ? 10_000 : false,
  })

  const { data: botSnapshots } = useQuery({
    queryKey: ['bot-snapshots', runId],
    queryFn: () => getBotSnapshots(runId),
  })

  useEffect(() => {
    if (run?.status === 'RUNNING') {
      const timer = setInterval(() => {
        qc.invalidateQueries({ queryKey: ['run', runId] })
      }, 10_000)
      return () => clearInterval(timer)
    }
  }, [run?.status, runId, qc])

  const cancelMutation = useMutation({
    mutationFn: () => cancelRun(runId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['run', runId] })
      message.success('Run cancelled')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const cleanupMutation = useMutation({
    mutationFn: () => cleanupRun(runId),
    onSuccess: () => {
      message.success('Cleanup initiated')
    },
    onError: (err: Error) => message.error(err.message),
  })

  if (isLoading) return <Spin size="large" />
  if (error) return <Alert type="error" message={(error as Error).message} />
  if (!run) return <Alert type="warning" message="Run not found" />

  const completed = progress?.completed ?? 0
  const total = progress?.total ?? 0
  const progressPercent = total > 0 ? Math.round((completed / total) * 100) : 0

  const replayPrColumns = [
    {
      title: 'Original PR#',
      key: 'originalPr',
      render: (_: unknown, r: ReplayPr) => `#${r.suitePr.originalPrNumber}`,
      width: 110,
    },
    {
      title: 'PR Title',
      key: 'title',
      render: (_: unknown, r: ReplayPr) => r.suitePr.title ?? '—',
      ellipsis: true,
    },
    {
      title: 'Bot',
      key: 'bot',
      render: (_: unknown, r: ReplayPr) => <Tag>{r.bot.name}</Tag>,
      width: 120,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: replayStatusTag,
      width: 110,
    },
    {
      title: 'Mirror PR',
      key: 'mirrorPr',
      render: (_: unknown, r: ReplayPr) =>
        r.mirrorPrUrl ? (
          <a href={r.mirrorPrUrl} target="_blank" rel="noopener noreferrer">
            #{r.mirrorPrNumber}
          </a>
        ) : (
          '—'
        ),
      width: 100,
    },
    {
      title: 'Error',
      dataIndex: 'errorMessage',
      key: 'error',
      render: (v: string | null) =>
        v ? <Text type="danger" ellipsis={{ tooltip: v }}>{v}</Text> : '—',
      ellipsis: true,
    },
    {
      title: 'Completed',
      dataIndex: 'completedAt',
      key: 'completedAt',
      render: (v: string | null) => (v ? dayjs(v).format('MMM D HH:mm') : '—'),
      width: 130,
    },
    {
      title: '',
      key: 'actions',
      render: (_: unknown, r: ReplayPr) => (
        <Button size="small" onClick={() => navigate(`/replay-prs/${r.id}`)}>
          View
        </Button>
      ),
      width: 70,
    },
  ]

  const progressTab = (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={4}>
            <Statistic title="Total" value={progress?.total ?? 0} />
          </Col>
          <Col span={4}>
            <Statistic title="Pending" value={progress?.pending ?? 0} />
          </Col>
          <Col span={4}>
            <Statistic title="Creating" value={progress?.creating ?? 0} />
          </Col>
          <Col span={4}>
            <Statistic title="Waiting" value={progress?.waiting ?? 0} />
          </Col>
          <Col span={4}>
            <Statistic title="Completed" value={progress?.completed ?? 0} valueStyle={{ color: 'green' }} />
          </Col>
          <Col span={4}>
            <Statistic title="Failed" value={progress?.failed ?? 0} valueStyle={{ color: 'red' }} />
          </Col>
        </Row>
        <Progress
          percent={progressPercent}
          status={run.status === 'FAILED' ? 'exception' : run.status === 'COMPLETED' ? 'success' : 'active'}
          style={{ marginTop: 16 }}
        />
      </Card>

      <Table
        dataSource={replayPrs}
        columns={replayPrColumns}
        rowKey="id"
        size="small"
        pagination={{ pageSize: 20 }}
        scroll={{ x: true }}
      />
    </div>
  )

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <Title level={2} style={{ margin: 0 }}>
            {run.name}
          </Title>
          <Space style={{ marginTop: 4 }}>
            {statusTag(run.status)}
            <Text type="secondary">Suite: {run.suite.name}</Text>
          </Space>
        </div>
        <Space>
          {run.status === 'RUNNING' && (
            <Popconfirm
              title="Cancel this run?"
              onConfirm={() => cancelMutation.mutate()}
            >
              <Button danger loading={cancelMutation.isPending}>
                Cancel Run
              </Button>
            </Popconfirm>
          )}
          {run.status === 'COMPLETED' && (
            <Popconfirm
              title="Clean up mirror PRs for this run?"
              onConfirm={() => cleanupMutation.mutate()}
            >
              <Button loading={cleanupMutation.isPending}>Cleanup PRs</Button>
            </Popconfirm>
          )}
          <Button onClick={() => window.open(exportRunCsv(runId))}>Export CSV</Button>
          <Button onClick={() => window.open(exportRunSarif(runId))}>Export SARIF</Button>
          <Button type="primary" onClick={() => navigate(`/reports/${runId}`)}>
            View Report
          </Button>
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="Bots">
            {run.bots.map((b) => (
              <Tag key={b.id}>{b.name}</Tag>
            ))}
          </Descriptions.Item>
          <Descriptions.Item label="Concurrency">{run.concurrency}</Descriptions.Item>
          <Descriptions.Item label="Golden Dataset">
            {run.goldenDatasetEnabled ? 'Enabled' : 'Disabled'}
          </Descriptions.Item>
          <Descriptions.Item label="Started">
            {run.startedAt ? dayjs(run.startedAt).format('MMM D, YYYY HH:mm') : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Completed">
            {run.completedAt ? dayjs(run.completedAt).format('MMM D, YYYY HH:mm') : '—'}
          </Descriptions.Item>
          {run.errorMessage && (
            <Descriptions.Item label="Error">
              <Text type="danger">{run.errorMessage}</Text>
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      <Tabs
        defaultActiveKey="progress"
        items={[
          { key: 'progress', label: 'Progress', children: progressTab },
          {
            key: 'report',
            label: 'Report',
            children: (
              <div>
                <Link to={`/reports/${runId}`}>
                  <Button type="primary">Open Full Report</Button>
                </Link>
              </div>
            ),
          },
          {
            key: 'snapshots',
            label: 'Bot Snapshots',
            children: (
              <Table
                dataSource={botSnapshots}
                rowKey="id"
                size="small"
                pagination={false}
                columns={[
                  { title: 'Bot Name', dataIndex: 'name', key: 'name' },
                  {
                    title: 'Workflow File',
                    dataIndex: 'workflowFileName',
                    key: 'workflowFileName',
                    render: (v: string | null) => v ?? '—',
                  },
                  { title: 'Wait Strategy', dataIndex: 'waitStrategy', key: 'waitStrategy' },
                  {
                    title: 'Timeout (s)',
                    dataIndex: 'timeoutSeconds',
                    key: 'timeoutSeconds',
                  },
                  {
                    title: 'Snapshotted At',
                    dataIndex: 'snapshottedAt',
                    key: 'snapshottedAt',
                    render: (v: string) => dayjs(v).format('MMM D, YYYY HH:mm'),
                  },
                ] as import('antd').TableColumnsType<BotSnapshot>}
              />
            ),
          },
        ]}
      />
    </div>
  )
}
