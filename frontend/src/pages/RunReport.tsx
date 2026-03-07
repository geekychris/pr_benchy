import { useState } from 'react'
import {
  Typography,
  Card,
  Table,
  Spin,
  Alert,
  Row,
  Col,
  Statistic,
  Tag,
  Descriptions,
  Button,
  Select,
  Space,
} from 'antd'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import { getRunReport, getComparisonReport, getRuns, getSignificance } from '../api/endpoints'
import type { BotStats, BotComparison } from '../api/types'

const { Title, Text } = Typography

export default function RunReport() {
  const { runId } = useParams<{ runId: string }>()
  const id = Number(runId)
  const [sigRunA, setSigRunA] = useState<number | undefined>(undefined)
  const [sigRunB, setSigRunB] = useState<number | undefined>(undefined)
  const [sigEnabled, setSigEnabled] = useState(false)

  const { data: report, isLoading, error } = useQuery({
    queryKey: ['run-report', id],
    queryFn: () => getRunReport(id),
  })

  const { data: comparison } = useQuery({
    queryKey: ['comparison-report', id],
    queryFn: () => getComparisonReport(id),
  })

  const { data: allRuns } = useQuery({
    queryKey: ['runs'],
    queryFn: () => getRuns(),
  })

  const { data: significance, isLoading: sigLoading } = useQuery({
    queryKey: ['significance', sigRunA, sigRunB],
    queryFn: () => getSignificance(sigRunA!, sigRunB!),
    enabled: sigEnabled && sigRunA !== undefined && sigRunB !== undefined,
  })

  if (isLoading) return <Spin size="large" />
  if (error) return <Alert type="error" message={(error as Error).message} />
  if (!report) return <Alert type="warning" message="Report not found" />

  const chartData = report.botStats.map((s) => ({
    name: s.botName,
    Valid: s.validCount,
    Invalid: s.invalidCount,
    Duplicate: s.duplicateCount,
    'Needs Review': s.needsReviewCount,
    Ungraded: s.ungradedCount,
  }))

  const botStatsColumns = [
    { title: 'Bot', dataIndex: 'botName', key: 'botName' },
    { title: 'Total', dataIndex: 'totalComments', key: 'total' },
    { title: 'Review Comments', dataIndex: 'reviewComments', key: 'reviewComments' },
    { title: 'PR Comments', dataIndex: 'prComments', key: 'prComments' },
    {
      title: 'Valid',
      dataIndex: 'validCount',
      key: 'valid',
      render: (v: number) => <Text style={{ color: 'green' }}>{v}</Text>,
    },
    {
      title: 'Invalid',
      dataIndex: 'invalidCount',
      key: 'invalid',
      render: (v: number) => <Text style={{ color: 'red' }}>{v}</Text>,
    },
    {
      title: 'Duplicate',
      dataIndex: 'duplicateCount',
      key: 'duplicate',
      render: (v: number) => <Text style={{ color: 'orange' }}>{v}</Text>,
    },
    {
      title: 'Needs Review',
      dataIndex: 'needsReviewCount',
      key: 'needsReview',
      render: (v: number) => <Text style={{ color: '#1677ff' }}>{v}</Text>,
    },
    {
      title: 'Ungraded',
      dataIndex: 'ungradedCount',
      key: 'ungraded',
      render: (v: number) => <Text type="secondary">{v}</Text>,
    },
  ]

  const comparisonColumns = [
    { title: 'Bot', dataIndex: 'botName', key: 'botName' },
    { title: 'True Positives', dataIndex: 'truePositives', key: 'tp' },
    { title: 'False Positives', dataIndex: 'falsePositives', key: 'fp' },
    { title: 'False Negatives', dataIndex: 'falseNegatives', key: 'fn' },
    {
      title: 'Precision',
      dataIndex: 'precision',
      key: 'precision',
      render: (v: number) => `${(v * 100).toFixed(1)}%`,
    },
    {
      title: 'Recall',
      dataIndex: 'recall',
      key: 'recall',
      render: (v: number) => `${(v * 100).toFixed(1)}%`,
    },
    {
      title: 'F1 Score',
      dataIndex: 'f1Score',
      key: 'f1',
      render: (v: number) => (
        <Text strong style={{ color: v > 0.7 ? 'green' : v > 0.4 ? 'orange' : 'red' }}>
          {(v * 100).toFixed(1)}%
        </Text>
      ),
    },
    {
      title: '',
      key: 'trend',
      render: (_: unknown, row: BotComparison) => (
        <Link to={`/trend?botId=${row.botId}&suiteId=${report?.runId ?? ''}`}>
          <Button size="small" type="link">View Trend</Button>
        </Link>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Run Report: {report.runName}</Title>

      <Card style={{ marginBottom: 16 }}>
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="Status">
            <Tag>{report.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Started">
            {report.startedAt ? dayjs(report.startedAt).format('MMM D, YYYY HH:mm') : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Completed">
            {report.completedAt ? dayjs(report.completedAt).format('MMM D, YYYY HH:mm') : '—'}
          </Descriptions.Item>
        </Descriptions>
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={8}>
            <Statistic title="Total Replay PRs" value={report.totalReplayPrs} />
          </Col>
          <Col span={8}>
            <Statistic
              title="Completed"
              value={report.completedReplayPrs}
              valueStyle={{ color: 'green' }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="Failed"
              value={report.failedReplayPrs}
              valueStyle={{ color: report.failedReplayPrs > 0 ? 'red' : undefined }}
            />
          </Col>
        </Row>
      </Card>

      <Card title="Bot Comment Statistics" style={{ marginBottom: 16 }}>
        <Table
          dataSource={report.botStats}
          columns={botStatsColumns}
          rowKey="botId"
          pagination={false}
          size="small"
        />
      </Card>

      {chartData.length > 0 && (
        <Card title="Verdicts by Bot" style={{ marginBottom: 16 }}>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Bar dataKey="Valid" fill="#52c41a" />
              <Bar dataKey="Invalid" fill="#ff4d4f" />
              <Bar dataKey="Duplicate" fill="#fa8c16" />
              <Bar dataKey="Needs Review" fill="#1677ff" />
              <Bar dataKey="Ungraded" fill="#d9d9d9" />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      )}

      {comparison && (
        <Card
          title={`Comparison Report (Golden Dataset: ${comparison.goldenEntryCount} entries)`}
          style={{ marginBottom: 16 }}
        >
          <Table
            dataSource={comparison.botComparisons}
            columns={comparisonColumns}
            rowKey="botId"
            pagination={false}
            size="small"
          />
        </Card>
      )}

      <Card title="Statistical Significance Test">
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 220 }}
            placeholder="Run A"
            value={sigRunA}
            onChange={(v) => { setSigRunA(v); setSigEnabled(false) }}
            options={allRuns?.map((r) => ({ label: r.name, value: r.id })) ?? []}
          />
          <Select
            style={{ width: 220 }}
            placeholder="Run B"
            value={sigRunB}
            onChange={(v) => { setSigRunB(v); setSigEnabled(false) }}
            options={allRuns?.map((r) => ({ label: r.name, value: r.id })) ?? []}
          />
          <Button
            type="primary"
            loading={sigLoading}
            disabled={sigRunA === undefined || sigRunB === undefined}
            onClick={() => setSigEnabled(true)}
          >
            Test Significance
          </Button>
        </Space>
        {significance && (
          <Alert
            type={
              !significance.significant
                ? 'success'
                : significance.runBF1 > significance.runAF1
                ? 'warning'
                : 'error'
            }
            message={significance.interpretation}
            description={
              <span>
                {significance.runAName} F1: {(significance.runAF1 * 100).toFixed(1)}% vs{' '}
                {significance.runBName} F1: {(significance.runBF1 * 100).toFixed(1)}% —
                p-value: {significance.pValue.toFixed(4)}
                {significance.significant ? ' (statistically significant)' : ' (not significant)'}
              </span>
            }
          />
        )}
      </Card>
    </div>
  )
}
