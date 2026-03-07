import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Card, Table, Button, Space, Typography, Tag, Modal, Form, Input, Select,
  InputNumber, Switch, message, Popconfirm, Tooltip, Slider,
} from 'antd'
import {
  PlusOutlined, EditOutlined, DeleteOutlined, PlayCircleOutlined, PauseCircleOutlined
} from '@ant-design/icons'
import {
  getScheduledRuns, createScheduledRun, updateScheduledRun, deleteScheduledRun,
  triggerScheduledRun, getBots, getSuites
} from '../api/endpoints'
import type { ScheduledRun, CreateScheduledRunRequest } from '../api/types'
import dayjs from 'dayjs'

const { Title, Text } = Typography

const CRON_PRESETS = [
  { label: '0 0 * * MON (weekly Monday)', value: '0 0 * * 1' },
  { label: '0 0 * * * (daily midnight)', value: '0 0 * * *' },
  { label: '0 * * * * (hourly)', value: '0 * * * *' },
  { label: '0 */6 * * * (every 6 hours)', value: '0 */6 * * *' },
  { label: '0 9 * * 1-5 (weekdays 9am)', value: '0 9 * * 1-5' },
]

export default function ScheduledRuns() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ScheduledRun | null>(null)
  const [form] = Form.useForm()

  const { data: scheduledRuns = [], isLoading } = useQuery({
    queryKey: ['scheduled-runs'],
    queryFn: () => getScheduledRuns(),
    refetchInterval: 30_000,
  })

  const { data: bots = [] } = useQuery({ queryKey: ['bots'], queryFn: getBots })
  const { data: suites = [] } = useQuery({ queryKey: ['suites'], queryFn: getSuites })

  const createMutation = useMutation({
    mutationFn: createScheduledRun,
    onSuccess: () => { message.success('Scheduled run created'); qc.invalidateQueries({ queryKey: ['scheduled-runs'] }); closeModal() },
    onError: () => message.error('Failed to create scheduled run'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<ScheduledRun> }) => updateScheduledRun(id, data),
    onSuccess: () => { message.success('Updated'); qc.invalidateQueries({ queryKey: ['scheduled-runs'] }); closeModal() },
    onError: () => message.error('Failed to update'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteScheduledRun,
    onSuccess: () => { message.success('Deleted'); qc.invalidateQueries({ queryKey: ['scheduled-runs'] }) },
  })

  const triggerMutation = useMutation({
    mutationFn: triggerScheduledRun,
    onSuccess: (run) => { message.success(`Run #${run.id} started`); qc.invalidateQueries({ queryKey: ['runs'] }) },
    onError: () => message.error('Failed to trigger run'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => updateScheduledRun(id, { enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['scheduled-runs'] }),
  })

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true) }
  const openEdit = (sr: ScheduledRun) => {
    setEditing(sr)
    form.setFieldsValue({
      ...sr,
      suiteId: sr.suite.id,
      botIds: sr.botIds.split(',').map(Number).filter(Boolean),
    })
    setModalOpen(true)
  }
  const closeModal = () => { setModalOpen(false); setEditing(null) }

  const handleSubmit = (values: CreateScheduledRunRequest & { suiteId: number }) => {
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: values as Partial<ScheduledRun> })
    } else {
      createMutation.mutate(values)
    }
  }

  const columns = [
    { title: 'Name', dataIndex: 'name', key: 'name', ellipsis: true },
    { title: 'Suite', key: 'suite', render: (_: unknown, row: ScheduledRun) => row.suite.name },
    { title: 'Cron', dataIndex: 'cronExpression', key: 'cron', width: 130,
      render: (v: string) => <Text code>{v}</Text> },
    { title: 'Next Run', dataIndex: 'nextRunAt', key: 'nextRunAt', width: 160,
      render: (v: string | null) => v ? dayjs(v).format('MMM D, HH:mm') : '—' },
    { title: 'Last Status', dataIndex: 'lastRunStatus', key: 'lastRunStatus', width: 110,
      render: (v: string | null) => v ? (
        <Tag color={v === 'COMPLETED' ? 'green' : v === 'FAILED' ? 'red' : 'blue'}>{v}</Tag>
      ) : '—' },
    { title: 'Enabled', key: 'enabled', width: 90,
      render: (_: unknown, row: ScheduledRun) => (
        <Switch
          size="small"
          checked={row.enabled}
          loading={toggleMutation.isPending}
          onChange={(enabled) => toggleMutation.mutate({ id: row.id, enabled })}
        />
      ),
    },
    {
      title: 'Actions', key: 'actions', width: 140,
      render: (_: unknown, row: ScheduledRun) => (
        <Space>
          <Button
            size="small"
            icon={<PlayCircleOutlined />}
            onClick={() => triggerMutation.mutate(row.id)}
            loading={triggerMutation.isPending}
          >
            Run Now
          </Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)} />
          <Popconfirm title="Delete this scheduled run?" onConfirm={() => deleteMutation.mutate(row.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Scheduled Runs</Title>
      <Card
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Add Schedule
          </Button>
        }
      >
        <Table
          rowKey="id"
          dataSource={scheduledRuns}
          columns={columns}
          loading={isLoading}
          size="small"
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title={editing ? 'Edit Schedule' : 'New Scheduled Run'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="e.g. Nightly Benchmark" />
          </Form.Item>
          <Form.Item name="suiteId" label="Suite" rules={[{ required: true }]}>
            <Select options={suites.map((s) => ({ label: s.name, value: s.id }))} />
          </Form.Item>
          <Form.Item name="botIds" label="Bots" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              options={bots.map((b) => ({ label: b.name, value: b.id }))}
              placeholder="Select bots"
            />
          </Form.Item>
          <Form.Item name="cronExpression" label="Cron Expression" rules={[{ required: true }]}>
            <Input
              placeholder="0 0 * * *"
              addonAfter={
                <Select
                  style={{ width: 200 }}
                  placeholder="Presets"
                  options={CRON_PRESETS}
                  onChange={(v) => form.setFieldValue('cronExpression', v)}
                  allowClear
                />
              }
            />
          </Form.Item>
          <Form.Item name="concurrency" label={`Concurrency`} initialValue={3}>
            <Slider min={1} max={10} marks={{ 1: '1', 5: '5', 10: '10' }} />
          </Form.Item>
          <Form.Item name="goldenDatasetEnabled" label="Golden Dataset" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
