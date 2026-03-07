import { useState } from 'react'
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Space,
  Tag,
  Typography,
  Popconfirm,
  Spin,
  Alert,
  message,
} from 'antd'
import { PlusOutlined, SyncOutlined, DeleteOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { getRepos, createRepo, deleteRepo, syncRepo } from '../api/endpoints'
import type { ExemplarRepo } from '../api/types'

const { Title } = Typography

export default function Repositories() {
  const [modalOpen, setModalOpen] = useState(false)
  const [syncingIds, setSyncingIds] = useState<Set<number>>(new Set())
  const [form] = Form.useForm()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: repos, isLoading, error } = useQuery({
    queryKey: ['repos'],
    queryFn: getRepos,
  })

  const createMutation = useMutation({
    mutationFn: createRepo,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['repos'] })
      message.success('Repository added')
      setModalOpen(false)
      form.resetFields()
    },
    onError: (err: Error) => message.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRepo,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['repos'] })
      message.success('Repository deleted')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const handleSync = async (id: number) => {
    setSyncingIds((prev) => new Set(prev).add(id))
    try {
      await syncRepo(id)
      message.success('Sync triggered')
      qc.invalidateQueries({ queryKey: ['repos'] })
    } catch (err) {
      message.error((err as Error).message)
    } finally {
      setSyncingIds((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
    }
  }

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: ExemplarRepo) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/suites?repoId=${record.id}`)}>
          {name}
        </Button>
      ),
    },
    {
      title: 'GitHub URL',
      dataIndex: 'githubUrl',
      key: 'githubUrl',
      render: (url: string) => (
        <a href={url} target="_blank" rel="noopener noreferrer">
          {url}
        </a>
      ),
    },
    {
      title: 'Mirror',
      key: 'mirror',
      render: (_: unknown, record: ExemplarRepo) =>
        record.mirrorRepoUrl ? (
          <a href={record.mirrorRepoUrl} target="_blank" rel="noopener noreferrer">
            {record.mirrorOrg}/{record.mirrorRepoName}
          </a>
        ) : (
          <span style={{ color: '#999' }}>—</span>
        ),
    },
    {
      title: 'Sync Status',
      dataIndex: 'syncStatus',
      key: 'syncStatus',
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'Last Synced',
      dataIndex: 'lastSyncedAt',
      key: 'lastSyncedAt',
      render: (v: string | null) => (v ? dayjs(v).format('MMM D, YYYY HH:mm') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: ExemplarRepo) => (
        <Space>
          <Button
            icon={<SyncOutlined spin={syncingIds.has(record.id)} />}
            size="small"
            onClick={() => handleSync(record.id)}
            loading={syncingIds.has(record.id)}
          >
            Sync
          </Button>
          <Popconfirm
            title="Delete this repository?"
            description="This will also delete all associated suites and runs."
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
          Repositories
        </Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setModalOpen(true)}
        >
          Add Repository
        </Button>
      </div>

      <Table dataSource={repos} columns={columns} rowKey="id" />

      <Modal
        title="Add Repository"
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
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="my-repo" />
          </Form.Item>
          <Form.Item
            name="githubUrl"
            label="GitHub URL"
            rules={[{ required: true }, { type: 'url' }]}
          >
            <Input placeholder="https://github.com/org/repo" />
          </Form.Item>
          <Form.Item name="mirrorOrg" label="Mirror Organization">
            <Input placeholder="my-mirror-org" />
          </Form.Item>
          <Form.Item name="mirrorRepoName" label="Mirror Repo Name">
            <Input placeholder="my-repo-mirror" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
