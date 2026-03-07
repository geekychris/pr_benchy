import { useState } from 'react'
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Space,
  Typography,
  Popconfirm,
  Spin,
  Alert,
  message,
  Tag,
} from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { getSuites, createSuite, deleteSuite, getRepos } from '../api/endpoints'
import type { BenchmarkSuite } from '../api/types'

const { Title } = Typography

export default function Suites() {
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const repoIdParam = searchParams.get('repoId')
  const qc = useQueryClient()

  const { data: suites, isLoading, error } = useQuery({
    queryKey: ['suites', repoIdParam],
    queryFn: () => getSuites(repoIdParam ? Number(repoIdParam) : undefined),
  })

  const { data: repos } = useQuery({ queryKey: ['repos'], queryFn: getRepos })

  const createMutation = useMutation({
    mutationFn: createSuite,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suites'] })
      message.success('Suite created')
      setModalOpen(false)
      form.resetFields()
    },
    onError: (err: Error) => message.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteSuite,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suites'] })
      message.success('Suite deleted')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: BenchmarkSuite) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/suites/${record.id}`)}>
          {name}
        </Button>
      ),
    },
    {
      title: 'Repository',
      key: 'repo',
      render: (_: unknown, record: BenchmarkSuite) => (
        <Tag>{record.exemplarRepo.name}</Tag>
      ),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => dayjs(v).format('MMM D, YYYY HH:mm'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: BenchmarkSuite) => (
        <Space>
          <Button size="small" onClick={() => navigate(`/suites/${record.id}`)}>
            Open
          </Button>
          <Popconfirm
            title="Delete this suite?"
            onConfirm={() => deleteMutation.mutate(record.id)}
            okText="Delete"
            okType="danger"
          >
            <Button icon={<DeleteOutlined />} size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  if (isLoading) return <Spin size="large" />
  if (error) return <Alert type="error" message={(error as Error).message} />

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Benchmark Suites
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          Create Suite
        </Button>
      </div>

      <Table dataSource={suites} columns={columns} rowKey="id" />

      <Modal
        title="Create Suite"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) =>
            createMutation.mutate({
              name: values.name,
              description: values.description,
              exemplarRepoId: values.exemplarRepoId,
            })
          }
          initialValues={{ exemplarRepoId: repoIdParam ? Number(repoIdParam) : undefined }}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="My Benchmark Suite" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Optional description" />
          </Form.Item>
          <Form.Item
            name="exemplarRepoId"
            label="Repository"
            rules={[{ required: true }]}
          >
            <Select placeholder="Select a repository">
              {repos?.map((r) => (
                <Select.Option key={r.id} value={r.id}>
                  {r.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
