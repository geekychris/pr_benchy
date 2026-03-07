import { useState } from 'react'
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  Space,
  Typography,
  Popconfirm,
  Spin,
  Alert,
  message,
  Tag,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { getBots, createBot, updateBot, deleteBot } from '../api/endpoints'
import type { Bot } from '../api/types'

const { Title } = Typography

export default function Bots() {
  const [modalOpen, setModalOpen] = useState(false)
  const [editingBot, setEditingBot] = useState<Bot | null>(null)
  const [form] = Form.useForm()
  const qc = useQueryClient()

  const { data: bots, isLoading, error } = useQuery({
    queryKey: ['bots'],
    queryFn: getBots,
  })

  const createMutation = useMutation({
    mutationFn: createBot,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bots'] })
      message.success('Bot created')
      closeModal()
    },
    onError: (err: Error) => message.error(err.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<Bot> }) => updateBot(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bots'] })
      message.success('Bot updated')
      closeModal()
    },
    onError: (err: Error) => message.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteBot,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bots'] })
      message.success('Bot deleted')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const openCreate = () => {
    setEditingBot(null)
    form.resetFields()
    form.setFieldsValue({ waitStrategy: 'CHECKS', timeoutSeconds: 300 })
    setModalOpen(true)
  }

  const openEdit = (bot: Bot) => {
    setEditingBot(bot)
    form.setFieldsValue(bot)
    setModalOpen(true)
  }

  const closeModal = () => {
    setModalOpen(false)
    setEditingBot(null)
    form.resetFields()
  }

  const handleSubmit = (values: {
    name: string
    description?: string
    workflowFileName?: string
    workflowContent?: string
    waitStrategy: string
    timeoutSeconds: number
  }) => {
    if (editingBot) {
      updateMutation.mutate({ id: editingBot.id, data: values })
    } else {
      createMutation.mutate(values)
    }
  }

  const columns = [
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Workflow File',
      dataIndex: 'workflowFileName',
      key: 'workflowFileName',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Wait Strategy',
      dataIndex: 'waitStrategy',
      key: 'waitStrategy',
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'Timeout (s)',
      dataIndex: 'timeoutSeconds',
      key: 'timeoutSeconds',
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => dayjs(v).format('MMM D, YYYY'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: Bot) => (
        <Space>
          <Button icon={<EditOutlined />} size="small" onClick={() => openEdit(record)}>
            Edit
          </Button>
          <Popconfirm
            title="Delete this bot?"
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
          Bots
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Add Bot
        </Button>
      </div>

      <Table dataSource={bots} columns={columns} rowKey="id" />

      <Modal
        title={editingBot ? 'Edit Bot' : 'Add Bot'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={700}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ waitStrategy: 'CHECKS', timeoutSeconds: 300 }}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="My PR Bot" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="workflowFileName" label="Workflow File Name">
            <Input placeholder=".github/workflows/pr-review.yml" />
          </Form.Item>
          <Form.Item name="workflowContent" label="Workflow Content">
            <Input.TextArea
              rows={8}
              placeholder="Paste GitHub Actions workflow YAML here"
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </Form.Item>
          <Form.Item name="waitStrategy" label="Wait Strategy" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="CHECKS">CHECKS</Select.Option>
              <Select.Option value="REVIEWS">REVIEWS</Select.Option>
              <Select.Option value="BOTH">BOTH</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="timeoutSeconds" label="Timeout (seconds)" rules={[{ required: true }]}>
            <InputNumber min={30} max={3600} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
