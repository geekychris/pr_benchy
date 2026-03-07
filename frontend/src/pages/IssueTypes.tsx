import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Card, Table, Button, Space, Typography, Tag, Modal, Form, Input, Select,
  Switch, message, Popconfirm, Tabs, Alert, Spin,
} from 'antd'
import dayjs from 'dayjs'
import { getIssueTypes, createIssueType, updateIssueType, deleteIssueType } from '../api/endpoints'
import type { IssueType } from '../api/types'

const { Title } = Typography

const CATEGORY_TABS = ['All', 'Security', 'Correctness', 'Performance', 'Style', 'Concurrency']
const CATEGORY_OPTIONS = ['Security', 'Correctness', 'Performance', 'Style', 'Concurrency', 'Other']
const SEVERITY_OPTIONS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

function severityColor(s: string | null) {
  if (!s) return 'default'
  const map: Record<string, string> = { CRITICAL: 'red', HIGH: 'orange', MEDIUM: 'gold', LOW: 'blue', INFO: 'cyan' }
  return map[s] ?? 'default'
}

export default function IssueTypes() {
  const qc = useQueryClient()
  const [categoryFilter, setCategoryFilter] = useState('All')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<IssueType | null>(null)
  const [form] = Form.useForm()

  const { data: issueTypes = [], isLoading, error } = useQuery({
    queryKey: ['issue-types'],
    queryFn: () => getIssueTypes(false),
  })

  const createMutation = useMutation({
    mutationFn: createIssueType,
    onSuccess: () => { message.success('Issue type created'); qc.invalidateQueries({ queryKey: ['issue-types'] }); closeModal() },
    onError: (err: Error) => message.error(err.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<IssueType> }) => updateIssueType(id, data),
    onSuccess: () => { message.success('Issue type updated'); qc.invalidateQueries({ queryKey: ['issue-types'] }); closeModal() },
    onError: (err: Error) => message.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteIssueType,
    onSuccess: () => { message.success('Issue type deleted'); qc.invalidateQueries({ queryKey: ['issue-types'] }) },
    onError: (err: Error) => message.error(err.message),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id: number) => updateIssueType(id, { active: false }),
    onSuccess: () => { message.success('Issue type deactivated'); qc.invalidateQueries({ queryKey: ['issue-types'] }) },
    onError: (err: Error) => message.error(err.message),
  })

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true) }
  const openEdit = (it: IssueType) => { setEditing(it); form.setFieldsValue(it); setModalOpen(true) }
  const closeModal = () => { setModalOpen(false); setEditing(null); form.resetFields() }

  const handleSubmit = (values: Partial<IssueType>) => {
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: values })
    } else {
      createMutation.mutate(values)
    }
  }

  const filtered = issueTypes.filter(
    (it) => categoryFilter === 'All' || it.category === categoryFilter
  )

  // Build grouped table data with rowSpan support
  const grouped = filtered.reduce<Record<string, IssueType[]>>((acc, it) => {
    const cat = it.category ?? 'Uncategorized'
    if (!acc[cat]) acc[cat] = []
    acc[cat].push(it)
    return acc
  }, {})
  const tableData = Object.values(grouped).flat()

  const columns = [
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      width: 140,
      render: (v: string | null, _row: IssueType, index: number) => {
        const cat = v ?? 'Uncategorized'
        const groupItems = tableData.filter((it) => (it.category ?? 'Uncategorized') === cat)
        const firstIndex = tableData.findIndex((it) => (it.category ?? 'Uncategorized') === cat)
        if (index === firstIndex) {
          return { children: <Tag>{cat}</Tag>, props: { rowSpan: groupItems.length } }
        }
        return { children: null, props: { rowSpan: 0 } }
      },
    },
    { title: 'Code', dataIndex: 'code', key: 'code', width: 160,
      render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Severity Hint', dataIndex: 'severityHint', key: 'severityHint', width: 130,
      render: (v: string | null) => v ? <Tag color={severityColor(v)}>{v}</Tag> : '—' },
    { title: 'Active', dataIndex: 'active', key: 'active', width: 80,
      render: (v: boolean) => <Tag color={v ? 'success' : 'default'}>{v ? 'Active' : 'Inactive'}</Tag> },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 130,
      render: (v: string) => dayjs(v).format('MMM D, YYYY') },
    {
      title: 'Actions', key: 'actions', width: 180,
      render: (_: unknown, row: IssueType) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(row)}>Edit</Button>
          {row.active && (
            <Popconfirm title="Deactivate this issue type?" onConfirm={() => deactivateMutation.mutate(row.id)}>
              <Button size="small" danger>Deactivate</Button>
            </Popconfirm>
          )}
          <Popconfirm title="Delete this issue type? This cannot be undone." onConfirm={() => deleteMutation.mutate(row.id)}>
            <Button size="small" danger type="text">Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Issue Type Taxonomy</Title>
        <Button type="primary" onClick={openCreate}>Add Issue Type</Button>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Tabs
          activeKey={categoryFilter}
          onChange={setCategoryFilter}
          items={CATEGORY_TABS.map((c) => ({ key: c, label: c }))}
        />
      </Card>

      {isLoading && <Spin size="large" />}
      {error && <Alert type="error" message={(error as Error).message} />}

      {!isLoading && !error && (
        <Table
          dataSource={tableData}
          columns={columns}
          rowKey="id"
          size="small"
          pagination={false}
          bordered
        />
      )}

      <Modal
        title={editing ? 'Edit Issue Type' : 'Add Issue Type'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit} initialValues={{ active: true }}>
          <Form.Item name="code" label="Code" rules={[{ required: true }]}
            extra="Use UPPERCASE_WITH_UNDERSCORES (e.g., NULL_CHECK)">
            <Input placeholder="NULL_CHECK" style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="Null Check" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Describe this issue type" />
          </Form.Item>
          <Form.Item name="category" label="Category">
            <Select allowClear placeholder="Select category"
              options={CATEGORY_OPTIONS.map((c) => ({ value: c, label: c }))} />
          </Form.Item>
          <Form.Item name="severityHint" label="Severity Hint">
            <Select allowClear placeholder="Suggested severity"
              options={SEVERITY_OPTIONS.map((s) => ({ value: s, label: s }))} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
