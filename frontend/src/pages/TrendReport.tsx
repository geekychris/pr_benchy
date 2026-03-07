import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Card, Select, Space, Typography, Alert, Spin, Table, Button,
} from 'antd'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { getBots, getSuites, getRuns, getBotTrend, getSignificance } from '../api/endpoints'
import type { TrendDataPoint } from '../api/types'

const { Title, Text } = Typography

export default function TrendReport() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [botId, setBotId] = useState<number | undefined>(
    searchParams.get('botId') ? Number(searchParams.get('botId')) : undefined
  )
  const [suiteId, setSuiteId] = useState<number | undefined>(
    searchParams.get('suiteId') ? Number(searchParams.get('suiteId')) : undefined
  )
  const [sigRunA, setSigRunA] = useState<number | undefined>(undefined)
  const [sigRunB, setSigRunB] = useState<number | undefined>(undefined)
  const [sigEnabled, setSigEnabled] = useState(false)

  const { data: bots } = useQuery({ queryKey: ['bots'], queryFn: () => getBots() })
  const { data: suites } = useQuery({ queryKey: ['suites'], queryFn: () => getSuites() })
  const { data: runs } = useQuery({ queryKey: ['runs'], queryFn: () => getRuns() })

  const { data: trend, isLoading, error } = useQuery({
    queryKey: ['bot-trend', botId, suiteId],
    queryFn: () => getBotTrend(botId!, suiteId!, 10),
    enabled: botId !== undefined && suiteId !== undefined,
  })

  const { data: significance, isLoading: sigLoading } = useQuery({
    queryKey: ['significance', sigRunA, sigRunB],
    queryFn: () => getSignificance(sigRunA!, sigRunB!),
    enabled: sigEnabled && sigRunA !== undefined && sigRunB !== undefined,
  })

  const handleBotChange = (v: number) => {
    setBotId(v)
    setSigEnabled(false)
    setSearchParams((prev) => { prev.set('botId', String(v)); return prev })
  }

  const handleSuiteChange = (v: number) => {
    setSuiteId(v)
    setSigEnabled(false)
    setSearchParams((prev) => { prev.set('suiteId', String(v)); return prev })
  }

  const chartData = (trend?.dataPoints ?? []).map((dp) => ({
    ...dp,
    date: dayjs(dp.startedAt).format('MMM D'),
    f1Pct: parseFloat((dp.f1Score * 100).toFixed(1)),
    precisionPct: parseFloat((dp.precision * 100).toFixed(1)),
    recallPct: parseFloat((dp.recall * 100).toFixed(1)),
  }))

  const tableColumns = [
    { title: 'Run', dataIndex: 'runName', key: 'runName' },
    {
      title: 'Date',
      dataIndex: 'startedAt',
      key: 'date',
      render: (v: string) => dayjs(v).format('MMM D, YYYY HH:mm'),
    },
    {
      title: 'F1',
      dataIndex: 'f1Score',
      key: 'f1',
      render: (v: number) => (
        <Text strong style={{ color: v > 0.7 ? 'green' : v > 0.4 ? 'orange' : 'red' }}>
          {(v * 100).toFixed(1)}%
        </Text>
      ),
    },
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
    { title: 'TP', dataIndex: 'truePositives', key: 'tp' },
    { title: 'FP', dataIndex: 'falsePositives', key: 'fp' },
    { title: 'FN', dataIndex: 'falseNegatives', key: 'fn' },
    { title: 'Total Comments', dataIndex: 'totalComments', key: 'total' },
  ]

  return (
    <div>
      <Title level={2}>Bot Performance Trend</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            style={{ width: 200 }}
            placeholder="Select bot"
            value={botId}
            onChange={handleBotChange}
            options={bots?.map((b) => ({ label: b.name, value: b.id })) ?? []}
          />
          <Select
            style={{ width: 200 }}
            placeholder="Select suite"
            value={suiteId}
            onChange={handleSuiteChange}
            options={suites?.map((s) => ({ label: s.name, value: s.id })) ?? []}
          />
        </Space>
      </Card>

      {!botId || !suiteId ? (
        <Alert type="info" message="Select a bot and suite to view trend data." />
      ) : isLoading ? (
        <Spin size="large" />
      ) : error ? (
        <Alert type="error" message={(error as Error).message} />
      ) : (
        <>
          <Card title={`Trend: ${trend?.botName}`} style={{ marginBottom: 16 }}>
            {chartData.length === 0 ? (
              <Text type="secondary">No trend data available.</Text>
            ) : (
              <ResponsiveContainer width="100%" height={320}>
                <LineChart data={chartData} margin={{ top: 8, right: 24, left: 0, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[0, 100]} tickFormatter={(v) => `${v}%`} />
                  <Tooltip formatter={(value: number) => `${value}%`} />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="f1Pct"
                    name="F1"
                    stroke="#1677ff"
                    strokeWidth={2}
                    dot={{ r: 4 }}
                    activeDot={{ r: 6 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="precisionPct"
                    name="Precision"
                    stroke="#52c41a"
                    strokeWidth={2}
                    dot={{ r: 4 }}
                    activeDot={{ r: 6 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="recallPct"
                    name="Recall"
                    stroke="#fa8c16"
                    strokeWidth={2}
                    dot={{ r: 4 }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Card>

          <Card title="Data Points" style={{ marginBottom: 16 }}>
            <Table
              dataSource={trend?.dataPoints ?? []}
              columns={tableColumns as import('antd').TableColumnsType<TrendDataPoint>}
              rowKey="runId"
              size="small"
              pagination={false}
            />
          </Card>
        </>
      )}

      <Card title="Compare Two Runs">
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 220 }}
            placeholder="Run A"
            value={sigRunA}
            onChange={(v) => { setSigRunA(v); setSigEnabled(false) }}
            options={runs?.map((r) => ({ label: r.name, value: r.id })) ?? []}
          />
          <Select
            style={{ width: 220 }}
            placeholder="Run B"
            value={sigRunB}
            onChange={(v) => { setSigRunB(v); setSigEnabled(false) }}
            options={runs?.map((r) => ({ label: r.name, value: r.id })) ?? []}
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
