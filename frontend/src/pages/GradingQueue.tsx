import { useState } from 'react'
import {
  Typography,
  Card,
  Table,
  Spin,
  Alert,
  Select,
  InputNumber,
  Space,
  Button,
  Progress,
  Tag,
  Checkbox,
  Form,
  Switch,
  Input,
  message,
  Row,
  Col,
  Statistic,
} from 'antd'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  getRuns,
  getSuites,
  getGradingQueue,
  getGradingProgress,
  bulkGrade,
} from '../api/endpoints'
import type { UngradedComment, BulkGradeRequest } from '../api/types'

const { Title, Text } = Typography

const VERDICT_COLORS: Record<string, string> = {
  VALID: 'success',
  INVALID: 'error',
  DUPLICATE: 'orange',
  NEEDS_REVIEW: 'processing',
}

export default function GradingQueue() {
  const qc = useQueryClient()
  const [selectedRunId, setSelectedRunId] = useState<number | undefined>(undefined)
  const [selectedSuiteId, setSelectedSuiteId] = useState<number | undefined>(undefined)
  const [limit, setLimit] = useState<number>(50)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [inlineVerdicts, setInlineVerdicts] = useState<Record<number, string>>({})
  const [bulkForm] = Form.useForm()

  const { data: runs } = useQuery({ queryKey: ['runs'], queryFn: () => getRuns() })
  const { data: suites } = useQuery({ queryKey: ['suites'], queryFn: () => getSuites() })

  const progressParams = {
    ...(selectedRunId !== undefined ? { runId: selectedRunId } : {}),
    ...(selectedSuiteId !== undefined ? { suiteId: selectedSuiteId } : {}),
  }

  const { data: gradingProgress } = useQuery({
    queryKey: ['grading-progress', selectedRunId, selectedSuiteId],
    queryFn: () => getGradingProgress(progressParams),
  })

  const {
    data: queue,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['grading-queue', selectedRunId, selectedSuiteId, limit],
    queryFn: () =>
      getGradingQueue({
        ...(selectedRunId !== undefined ? { runId: selectedRunId } : {}),
        ...(selectedSuiteId !== undefined ? { suiteId: selectedSuiteId } : {}),
        limit,
      }),
  })

  const bulkMutation = useMutation({
    mutationFn: (req: BulkGradeRequest) => bulkGrade(req),
    onSuccess: (result) => {
      message.success(
        `Graded ${result.successCount} comment(s). Skipped: ${result.skippedCount}.`
      )
      if (result.errors.length > 0) {
        message.error(`Errors: ${result.errors.join(', ')}`)
      }
      setSelectedIds([])
      bulkForm.resetFields()
      qc.invalidateQueries({ queryKey: ['grading-queue'] })
      qc.invalidateQueries({ queryKey: ['grading-progress'] })
    },
    onError: (err: Error) => message.error(err.message),
  })

  const handleBulkGrade = (values: {
    verdict: string
    severity?: string
    stars?: number
    flagged?: boolean
    notes?: string
  }) => {
    if (selectedIds.length === 0) {
      message.warning('No comments selected')
      return
    }
    bulkMutation.mutate({
      commentIds: selectedIds,
      commentTableType: 'REVIEW',
      verdict: values.verdict,
      severity: values.severity ?? null,
      stars: values.stars ?? null,
      flagged: values.flagged ?? null,
      notes: values.notes ?? null,
      graderId: null,
    })
  }

  const columns = [
    {
      title: '',
      key: 'select',
      width: 40,
      render: (_: unknown, row: UngradedComment) => (
        <Checkbox
          checked={selectedIds.includes(row.commentId)}
          onChange={(e) => {
            setSelectedIds((prev) =>
              e.target.checked
                ? [...prev, row.commentId]
                : prev.filter((id) => id !== row.commentId)
            )
          }}
        />
      ),
    },
    {
      title: 'PR#',
      key: 'pr',
      width: 80,
      render: (_: unknown, row: UngradedComment) => `#${row.originalPrNumber}`,
    },
    {
      title: 'PR Title',
      key: 'prTitle',
      ellipsis: true,
      render: (_: unknown, row: UngradedComment) => row.prTitle ?? '—',
    },
    {
      title: 'Bot',
      key: 'bot',
      width: 120,
      render: (_: unknown, row: UngradedComment) =>
        row.botName ? <Tag>{row.botName}</Tag> : '—',
    },
    {
      title: 'File:Line',
      key: 'location',
      width: 180,
      render: (_: unknown, row: UngradedComment) =>
        row.filePath
          ? `${row.filePath}${row.lineNumber != null ? `:${row.lineNumber}` : ''}`
          : '—',
      ellipsis: true,
    },
    {
      title: 'Type',
      dataIndex: 'commentType',
      key: 'commentType',
      width: 120,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: 'Body',
      key: 'body',
      ellipsis: true,
      render: (_: unknown, row: UngradedComment) =>
        row.body.length > 200 ? `${row.body.slice(0, 200)}…` : row.body,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_: unknown, row: UngradedComment) => (
        <Space size="small">
          <Select
            size="small"
            style={{ width: 130 }}
            placeholder="Quick grade"
            value={inlineVerdicts[row.commentId]}
            onChange={(v) => setInlineVerdicts((prev) => ({ ...prev, [row.commentId]: v }))}
          >
            {['VALID', 'INVALID', 'DUPLICATE', 'NEEDS_REVIEW'].map((v) => (
              <Select.Option key={v} value={v}>
                <Tag color={VERDICT_COLORS[v]}>{v}</Tag>
              </Select.Option>
            ))}
          </Select>
          <Button
            size="small"
            type="primary"
            disabled={!inlineVerdicts[row.commentId]}
            loading={bulkMutation.isPending}
            onClick={() => {
              const verdict = inlineVerdicts[row.commentId]
              if (!verdict) return
              bulkMutation.mutate({
                commentIds: [row.commentId],
                commentTableType: row.commentTableType,
                verdict,
                severity: null,
                stars: null,
                flagged: null,
                notes: null,
                graderId: null,
              })
            }}
          >
            Grade
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Grading Queue</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            style={{ width: 220 }}
            placeholder="Filter by run"
            allowClear
            value={selectedRunId}
            onChange={(v) => setSelectedRunId(v)}
            options={runs?.map((r) => ({ label: r.name, value: r.id })) ?? []}
          />
          <Select
            style={{ width: 220 }}
            placeholder="Filter by suite"
            allowClear
            value={selectedSuiteId}
            onChange={(v) => setSelectedSuiteId(v)}
            options={suites?.map((s) => ({ label: s.name, value: s.id })) ?? []}
          />
          <span>
            Limit:{' '}
            <InputNumber
              min={1}
              max={500}
              value={limit}
              onChange={(v) => setLimit(v ?? 50)}
              style={{ width: 80 }}
            />
          </span>
        </Space>
      </Card>

      {gradingProgress && (
        <Card style={{ marginBottom: 16 }}>
          <Row gutter={16} style={{ marginBottom: 8 }}>
            <Col span={4}>
              <Statistic title="Total" value={gradingProgress.totalComments} />
            </Col>
            <Col span={4}>
              <Statistic title="Graded" value={gradingProgress.gradedComments} valueStyle={{ color: 'green' }} />
            </Col>
            <Col span={4}>
              <Statistic title="Ungraded" value={gradingProgress.ungradedComments} valueStyle={{ color: 'orange' }} />
            </Col>
            <Col span={4}>
              <Statistic title="Valid" value={gradingProgress.validCount} />
            </Col>
            <Col span={4}>
              <Statistic title="Invalid" value={gradingProgress.invalidCount} />
            </Col>
            <Col span={4}>
              <Statistic title="Flagged" value={gradingProgress.flaggedCount} valueStyle={{ color: 'red' }} />
            </Col>
          </Row>
          <Progress
            percent={Math.round(gradingProgress.percentComplete)}
            status="active"
          />
        </Card>
      )}

      {selectedIds.length > 0 && (
        <Card
          title={`Bulk Grade (${selectedIds.length} selected)`}
          style={{ marginBottom: 16 }}
          extra={
            <Button size="small" onClick={() => setSelectedIds([])}>
              Clear Selection
            </Button>
          }
        >
          <Form form={bulkForm} layout="inline" onFinish={handleBulkGrade} style={{ flexWrap: 'wrap', gap: 8 }}>
            <Form.Item name="verdict" label="Verdict" rules={[{ required: true }]}>
              <Select style={{ width: 150 }}>
                {['VALID', 'INVALID', 'DUPLICATE', 'NEEDS_REVIEW'].map((v) => (
                  <Select.Option key={v} value={v}>
                    <Tag color={VERDICT_COLORS[v]}>{v}</Tag>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="severity" label="Severity">
              <Select style={{ width: 110 }} allowClear>
                {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'].map((s) => (
                  <Select.Option key={s} value={s}>{s}</Select.Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="stars" label="Stars">
              <Select style={{ width: 90 }} allowClear>
                {[0, 1, 2, 3, 4, 5].map((n) => (
                  <Select.Option key={n} value={n}>{n} ★</Select.Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="flagged" label="Flagged" valuePropName="checked">
              <Switch size="small" />
            </Form.Item>
            <Form.Item name="notes" label="Notes" style={{ width: '100%' }}>
              <Input.TextArea rows={2} placeholder="Optional notes" />
            </Form.Item>
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={bulkMutation.isPending}
              >
                Grade Selected
              </Button>
            </Form.Item>
          </Form>
        </Card>
      )}

      {isLoading && <Spin size="large" />}
      {error && <Alert type="error" message={(error as Error).message} />}

      {!isLoading && !error && (
        <Card>
          <div style={{ marginBottom: 8 }}>
            <Space>
              <Checkbox
                indeterminate={
                  selectedIds.length > 0 && selectedIds.length < (queue?.length ?? 0)
                }
                checked={
                  (queue?.length ?? 0) > 0 && selectedIds.length === (queue?.length ?? 0)
                }
                onChange={(e) =>
                  setSelectedIds(
                    e.target.checked ? (queue ?? []).map((c) => c.commentId) : []
                  )
                }
              >
                Select All
              </Checkbox>
              <Text type="secondary">{queue?.length ?? 0} ungraded comment(s)</Text>
            </Space>
          </div>
          <Table
            dataSource={queue}
            columns={columns}
            rowKey="commentId"
            size="small"
            pagination={{ pageSize: 25 }}
            scroll={{ x: true }}
          />
        </Card>
      )}
    </div>
  )
}
