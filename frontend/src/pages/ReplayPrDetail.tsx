import { useState } from 'react'
import {
  Row,
  Col,
  Card,
  Typography,
  Tag,
  Spin,
  Alert,
  Space,
  Button,
  Form,
  Select,
  Rate,
  Switch,
  Input,
  Divider,
  Modal,
  message,
  List,
} from 'antd'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  getReplayPrComments,
  getSuitePrOriginalComments,
  getGradings,
  createGrading,
  updateGrading,
  deleteGrading,
  promoteToGolden,
  getIssueTypes,
} from '../api/endpoints'
import type { ReviewComment, Grading, OriginalComment } from '../api/types'

const { Title, Text, Paragraph } = Typography

const VERDICT_COLORS: Record<string, string> = {
  VALID: 'success',
  INVALID: 'error',
  DUPLICATE: 'orange',
  NEEDS_REVIEW: 'processing',
}

function verdictTag(verdict: string) {
  return <Tag color={VERDICT_COLORS[verdict] ?? 'default'}>{verdict}</Tag>
}

interface GradingFormProps {
  comment: ReviewComment
  existingGrading?: Grading
  onSaved: () => void
}

function GradingForm({ comment, existingGrading, onSaved }: GradingFormProps) {
  const [form] = Form.useForm()
  const qc = useQueryClient()

  const createMutation = useMutation({
    mutationFn: createGrading,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['gradings', comment.id] })
      message.success('Grading saved')
      onSaved()
    },
    onError: (err: Error) => message.error(err.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<Grading> }) => updateGrading(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['gradings', comment.id] })
      message.success('Grading updated')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const handleSubmit = (values: {
    verdict: string
    severity?: string
    stars: number
    flagged: boolean
    notes?: string
  }) => {
    if (existingGrading) {
      updateMutation.mutate({ id: existingGrading.id, data: values })
    } else {
      createMutation.mutate({
        commentId: comment.id,
        commentTableType: 'REVIEW',
        graderType: 'HUMAN',
        verdict: values.verdict,
        severity: values.severity,
        stars: values.stars ?? 0,
        flagged: values.flagged ?? false,
        notes: values.notes,
      })
    }
  }

  return (
    <Form
      form={form}
      layout="inline"
      size="small"
      onFinish={handleSubmit}
      initialValues={
        existingGrading
          ? {
              verdict: existingGrading.verdict,
              severity: existingGrading.severity,
              stars: existingGrading.stars,
              flagged: existingGrading.flagged,
              notes: existingGrading.notes,
            }
          : { stars: 0, flagged: false }
      }
      style={{ flexWrap: 'wrap', gap: 8 }}
    >
      <Form.Item name="verdict" label="Verdict" rules={[{ required: true }]}>
        <Select style={{ width: 140 }}>
          <Select.Option value="VALID">VALID</Select.Option>
          <Select.Option value="INVALID">INVALID</Select.Option>
          <Select.Option value="DUPLICATE">DUPLICATE</Select.Option>
          <Select.Option value="NEEDS_REVIEW">NEEDS_REVIEW</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item name="severity" label="Severity">
        <Select style={{ width: 110 }} allowClear>
          <Select.Option value="CRITICAL">Critical</Select.Option>
          <Select.Option value="HIGH">High</Select.Option>
          <Select.Option value="MEDIUM">Medium</Select.Option>
          <Select.Option value="LOW">Low</Select.Option>
          <Select.Option value="INFO">Info</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item name="stars" label="Stars">
        <Rate count={5} />
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
          size="small"
          loading={createMutation.isPending || updateMutation.isPending}
        >
          {existingGrading ? 'Update' : 'Save'}
        </Button>
      </Form.Item>
    </Form>
  )
}

interface CommentCardProps {
  comment: ReviewComment
}

function CommentCard({ comment }: CommentCardProps) {
  const [showGradingForm, setShowGradingForm] = useState(false)
  const [promoteModalOpen, setPromoteModalOpen] = useState(false)
  const [promoteForm] = Form.useForm()
  const qc = useQueryClient()

  const { data: issueTypes } = useQuery({
    queryKey: ['issue-types'],
    queryFn: () => getIssueTypes(true),
    enabled: promoteModalOpen,
  })

  const { data: gradings } = useQuery({
    queryKey: ['gradings', comment.id],
    queryFn: () => getGradings(comment.id),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteGrading,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['gradings', comment.id] })
      message.success('Grading deleted')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const promoteMutation = useMutation({
    mutationFn: promoteToGolden,
    onSuccess: () => {
      message.success('Promoted to golden dataset')
      setPromoteModalOpen(false)
      promoteForm.resetFields()
    },
    onError: (err: Error) => message.error(err.message),
  })

  const firstGrading = gradings?.[0]

  return (
    <Card
      size="small"
      style={{ marginBottom: 12 }}
      extra={
        <Space>
          {firstGrading && verdictTag(firstGrading.verdict)}
          <Button size="small" onClick={() => setShowGradingForm((v) => !v)}>
            {showGradingForm ? 'Hide' : 'Grade'}
          </Button>
          <Button size="small" onClick={() => setPromoteModalOpen(true)}>
            Promote to Golden
          </Button>
        </Space>
      }
      title={
        <Space size="small">
          <Tag color="blue">{comment.commentType}</Tag>
          {comment.filePath && (
            <Text code style={{ fontSize: 12 }}>
              {comment.filePath}
              {comment.lineNumber ? `:${comment.lineNumber}` : ''}
            </Text>
          )}
          {comment.githubCreatedAt && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {dayjs(comment.githubCreatedAt).format('MMM D, YYYY HH:mm')}
            </Text>
          )}
        </Space>
      }
    >
      <Paragraph
        style={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: 13, marginBottom: 8 }}
      >
        {comment.body}
      </Paragraph>

      {gradings && gradings.length > 0 && (
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Gradings:
          </Text>
          <List
            size="small"
            dataSource={gradings}
            renderItem={(g) => (
              <List.Item
                actions={[
                  <Button
                    size="small"
                    danger
                    key="del"
                    onClick={() => deleteMutation.mutate(g.id)}
                  >
                    Delete
                  </Button>,
                ]}
              >
                <Space>
                  {verdictTag(g.verdict)}
                  {g.severity && <Tag>{g.severity}</Tag>}
                  {g.stars > 0 && <Rate disabled defaultValue={g.stars} count={5} style={{ fontSize: 12 }} />}
                  {g.flagged && <Tag color="red">Flagged</Tag>}
                  {g.notes && <Text type="secondary">{g.notes}</Text>}
                </Space>
              </List.Item>
            )}
          />
        </div>
      )}

      {showGradingForm && (
        <div style={{ marginTop: 8 }}>
          <Divider style={{ margin: '8px 0' }} />
          <GradingForm
            comment={comment}
            existingGrading={firstGrading}
            onSaved={() => setShowGradingForm(false)}
          />
        </div>
      )}

      <Modal
        title="Promote to Golden Dataset"
        open={promoteModalOpen}
        onCancel={() => {
          setPromoteModalOpen(false)
          promoteForm.resetFields()
        }}
        onOk={() => promoteForm.submit()}
        confirmLoading={promoteMutation.isPending}
      >
        <Form
          form={promoteForm}
          layout="vertical"
          onFinish={(values) =>
            promoteMutation.mutate({
              commentId: comment.id,
              commentTableType: 'REVIEW',
              issueType: values.issueType,
              description: values.description,
            })
          }
        >
          <Form.Item name="issueType" label="Issue Type">
            <Select
              allowClear
              showSearch
              placeholder="Select issue type"
              optionFilterProp="label"
            >
              {Object.entries(
                (issueTypes ?? []).reduce<Record<string, typeof issueTypes>>((acc, it) => {
                  const cat = it.category ?? 'Uncategorized'
                  if (!acc[cat]) acc[cat] = []
                  acc[cat]!.push(it)
                  return acc
                }, {})
              ).map(([category, items]) => (
                <Select.OptGroup key={category} label={category}>
                  {(items ?? []).map((it) => (
                    <Select.Option key={it.id} value={it.code} label={it.name}>
                      {it.name} <span style={{ color: '#888', fontSize: 12 }}>({it.code})</span>
                    </Select.Option>
                  ))}
                </Select.OptGroup>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Describe the issue" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

interface OriginalCommentCardProps {
  comment: OriginalComment
}

function OriginalCommentCard({ comment }: OriginalCommentCardProps) {
  return (
    <Card
      size="small"
      style={{ marginBottom: 12 }}
      title={
        <Space size="small">
          <Tag>{comment.commentType}</Tag>
          {comment.author && <Text strong>{comment.author}</Text>}
          {comment.filePath && (
            <Text code style={{ fontSize: 12 }}>
              {comment.filePath}
              {comment.lineNumber ? `:${comment.lineNumber}` : ''}
            </Text>
          )}
          {comment.githubCreatedAt && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {dayjs(comment.githubCreatedAt).format('MMM D, YYYY HH:mm')}
            </Text>
          )}
        </Space>
      }
    >
      <Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 13, marginBottom: 0 }}>
        {comment.body}
      </Paragraph>
    </Card>
  )
}

export default function ReplayPrDetail() {
  const { id } = useParams<{ id: string }>()
  const replayPrId = Number(id)

  const { data: comments, isLoading: commentsLoading, error: commentsError } = useQuery({
    queryKey: ['replay-pr-comments', replayPrId],
    queryFn: () => getReplayPrComments(replayPrId),
  })

  // To show original comments, we need the suitePrId. Since there's no /replay-prs/{id}
  // direct endpoint, we try to infer it from context. For now, allow user to provide
  // the suite PR ID via URL search params if needed, or show a note.
  // We'll check for suitePrId in searchParams.
  const searchParams = new URLSearchParams(window.location.search)
  const suitePrId = searchParams.get('suitePrId') ? Number(searchParams.get('suitePrId')) : null

  const { data: originalComments } = useQuery({
    queryKey: ['original-comments', suitePrId],
    queryFn: () => getSuitePrOriginalComments(suitePrId!),
    enabled: suitePrId !== null,
  })

  if (commentsLoading) return <Spin size="large" />
  if (commentsError) return <Alert type="error" message={(commentsError as Error).message} />

  return (
    <div>
      <Title level={2}>Replay PR Review</Title>
      <Row gutter={16}>
        <Col span={suitePrId ? 12 : 24}>
          <Card title={`Bot Comments (${comments?.length ?? 0})`}>
            {!comments || comments.length === 0 ? (
              <Text type="secondary">No bot comments found.</Text>
            ) : (
              comments.map((c) => <CommentCard key={c.id} comment={c} />)
            )}
          </Card>
        </Col>
        {suitePrId && (
          <Col span={12}>
            <Card title={`Original Human Comments (${originalComments?.length ?? 0})`}>
              {!originalComments || originalComments.length === 0 ? (
                <Text type="secondary">No original comments found.</Text>
              ) : (
                originalComments.map((c) => <OriginalCommentCard key={c.id} comment={c} />)
              )}
            </Card>
          </Col>
        )}
      </Row>
    </div>
  )
}
