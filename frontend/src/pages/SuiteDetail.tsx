import { useState } from 'react'
import {
  Row,
  Col,
  Card,
  Table,
  Button,
  Space,
  Typography,
  Tag,
  Spin,
  Alert,
  Modal,
  Form,
  Select,
  InputNumber,
  message,
  Popconfirm,
  Input,
  Switch,
} from 'antd'
import { PlusOutlined, DeleteOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  getSuite,
  getSuitePrs,
  getRepoPrs,
  addSuitePr,
  removeSuitePr,
  getBots,
  createRun,
} from '../api/endpoints'
import type { SuitePr, GitHubPr } from '../api/types'

const { Title, Text } = Typography

export default function SuiteDetail() {
  const { id } = useParams<{ id: string }>()
  const suiteId = Number(id)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [runModalOpen, setRunModalOpen] = useState(false)
  const [runForm] = Form.useForm()
  const [prStateFilter, setPrStateFilter] = useState('all')
  const [addingPrNums, setAddingPrNums] = useState<Set<number>>(new Set())

  const { data: suite, isLoading: suiteLoading, error: suiteError } = useQuery({
    queryKey: ['suite', suiteId],
    queryFn: () => getSuite(suiteId),
  })

  const { data: suitePrs, isLoading: suitePrsLoading } = useQuery({
    queryKey: ['suite-prs', suiteId],
    queryFn: () => getSuitePrs(suiteId),
  })

  const { data: githubPrs, isLoading: githubPrsLoading } = useQuery({
    queryKey: ['repo-prs', suite?.exemplarRepo.id, prStateFilter],
    queryFn: () => getRepoPrs(suite!.exemplarRepo.id, prStateFilter),
    enabled: !!suite,
  })

  const { data: bots } = useQuery({ queryKey: ['bots'], queryFn: getBots })

  const addPrMutation = useMutation({
    mutationFn: (prNumber: number) => addSuitePr(suiteId, prNumber),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suite-prs', suiteId] })
      message.success('PR added to suite')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const removePrMutation = useMutation({
    mutationFn: (prId: number) => removeSuitePr(suiteId, prId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suite-prs', suiteId] })
      message.success('PR removed from suite')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const createRunMutation = useMutation({
    mutationFn: createRun,
    onSuccess: (run) => {
      qc.invalidateQueries({ queryKey: ['runs'] })
      message.success('Run started!')
      setRunModalOpen(false)
      runForm.resetFields()
      navigate(`/runs/${run.id}`)
    },
    onError: (err: Error) => message.error(err.message),
  })

  const suitePrNumbers = new Set(suitePrs?.map((p) => p.originalPrNumber))

  const handleAddPr = async (prNumber: number) => {
    setAddingPrNums((prev) => new Set(prev).add(prNumber))
    try {
      await addPrMutation.mutateAsync(prNumber)
    } finally {
      setAddingPrNums((prev) => {
        const next = new Set(prev)
        next.delete(prNumber)
        return next
      })
    }
  }

  const githubPrColumns = [
    { title: '#', dataIndex: 'number', key: 'number', width: 70 },
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
    },
    { title: 'Author', dataIndex: 'author', key: 'author', width: 120 },
    {
      title: 'Merged',
      dataIndex: 'mergedAt',
      key: 'mergedAt',
      width: 160,
      render: (v: string | null) => (v ? dayjs(v).format('MMM D, YYYY') : '—'),
    },
    { title: 'Files', dataIndex: 'filesChanged', key: 'filesChanged', width: 70 },
    {
      title: '+/-',
      key: 'diff',
      width: 100,
      render: (_: unknown, r: GitHubPr) => (
        <span>
          <Text style={{ color: 'green' }}>+{r.additions}</Text>{' '}
          <Text style={{ color: 'red' }}>-{r.deletions}</Text>
        </span>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 80,
      render: (_: unknown, r: GitHubPr) =>
        suitePrNumbers.has(r.number) ? (
          <Tag color="success">In Suite</Tag>
        ) : (
          <Tag>Not Added</Tag>
        ),
    },
    {
      title: '',
      key: 'action',
      width: 100,
      render: (_: unknown, r: GitHubPr) =>
        suitePrNumbers.has(r.number) ? null : (
          <Button
            size="small"
            icon={<PlusOutlined />}
            loading={addingPrNums.has(r.number)}
            onClick={() => handleAddPr(r.number)}
          >
            Add
          </Button>
        ),
    },
  ]

  const suitePrColumns = [
    { title: '#', dataIndex: 'originalPrNumber', key: 'number', width: 70 },
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      render: (v: string | null) => v ?? '—',
      ellipsis: true,
    },
    {
      title: 'Author',
      dataIndex: 'author',
      key: 'author',
      width: 120,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Files',
      dataIndex: 'filesChanged',
      key: 'filesChanged',
      width: 70,
      render: (v: number | null) => v ?? '—',
    },
    {
      title: '',
      key: 'action',
      width: 80,
      render: (_: unknown, r: SuitePr) => (
        <Popconfirm
          title="Remove this PR from the suite?"
          onConfirm={() => removePrMutation.mutate(r.id)}
          okText="Remove"
          okType="danger"
        >
          <Button size="small" icon={<DeleteOutlined />} danger />
        </Popconfirm>
      ),
    },
  ]

  if (suiteLoading) return <Spin size="large" />
  if (suiteError) return <Alert type="error" message={(suiteError as Error).message} />
  if (!suite) return <Alert type="warning" message="Suite not found" />

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <Title level={2} style={{ margin: 0 }}>
            {suite.name}
          </Title>
          <Text type="secondary">
            {suite.exemplarRepo.name} &bull;{' '}
            {suite.description ?? 'No description'}
          </Text>
        </div>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          onClick={() => setRunModalOpen(true)}
          disabled={(suitePrs?.length ?? 0) === 0}
        >
          Start Run
        </Button>
      </div>

      <Row gutter={16}>
        <Col span={14}>
          <Card
            title="Available PRs from GitHub"
            extra={
              <Space>
                <span>State:</span>
                <Select
                  value={prStateFilter}
                  onChange={setPrStateFilter}
                  style={{ width: 100 }}
                  size="small"
                >
                  <Select.Option value="all">All</Select.Option>
                  <Select.Option value="open">Open</Select.Option>
                  <Select.Option value="closed">Closed</Select.Option>
                  <Select.Option value="merged">Merged</Select.Option>
                </Select>
              </Space>
            }
          >
            <Table
              dataSource={githubPrs}
              columns={githubPrColumns}
              rowKey="number"
              loading={githubPrsLoading}
              size="small"
              pagination={{ pageSize: 10 }}
              scroll={{ x: true }}
            />
          </Card>
        </Col>
        <Col span={10}>
          <Card
            title={`Suite PRs (${suitePrs?.length ?? 0})`}
          >
            <Table
              dataSource={suitePrs}
              columns={suitePrColumns}
              rowKey="id"
              loading={suitePrsLoading}
              size="small"
              pagination={false}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title="Start Benchmark Run"
        open={runModalOpen}
        onCancel={() => {
          setRunModalOpen(false)
          runForm.resetFields()
        }}
        onOk={() => runForm.submit()}
        confirmLoading={createRunMutation.isPending}
      >
        <Form
          form={runForm}
          layout="vertical"
          initialValues={{ concurrency: 3, goldenDatasetEnabled: false }}
          onFinish={(values) =>
            createRunMutation.mutate({
              suiteId,
              name: values.name,
              botIds: values.botIds,
              concurrency: values.concurrency,
              goldenDatasetEnabled: values.goldenDatasetEnabled,
            })
          }
        >
          <Form.Item name="name" label="Run Name" rules={[{ required: true }]}>
            <Input placeholder="My benchmark run" />
          </Form.Item>
          <Form.Item name="botIds" label="Bots" rules={[{ required: true }]}>
            <Select mode="multiple" placeholder="Select bots to run">
              {bots?.map((b) => (
                <Select.Option key={b.id} value={b.id}>
                  {b.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="concurrency" label="Concurrency">
            <InputNumber min={1} max={10} />
          </Form.Item>
          <Form.Item name="goldenDatasetEnabled" label="Enable Golden Dataset" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
