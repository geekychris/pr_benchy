import { useRef, useState } from 'react'
import {
  Table,
  Button,
  Typography,
  Tag,
  Switch,
  Space,
  Spin,
  Alert,
  Popconfirm,
  message,
  Modal,
  Input,
  Upload,
} from 'antd'
import {
  DownloadOutlined,
  UploadOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  getGoldenDataset,
  updateGoldenEntry,
  deleteGoldenEntry,
  exportGoldenDataset,
  importGoldenDataset,
} from '../api/endpoints'
import type { GoldenDatasetEntry } from '../api/types'

const { Title, Text } = Typography

export default function GoldenDataset() {
  const [activeOnly, setActiveOnly] = useState(true)
  const [importModalOpen, setImportModalOpen] = useState(false)
  const [importText, setImportText] = useState('')
  const qc = useQueryClient()

  const { data: entries, isLoading, error } = useQuery({
    queryKey: ['golden-dataset', activeOnly],
    queryFn: () => getGoldenDataset(activeOnly),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<GoldenDatasetEntry> }) =>
      updateGoldenEntry(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['golden-dataset'] })
    },
    onError: (err: Error) => message.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteGoldenEntry,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['golden-dataset'] })
      message.success('Entry deleted')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const importMutation = useMutation({
    mutationFn: importGoldenDataset,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['golden-dataset'] })
      message.success('Import successful')
      setImportModalOpen(false)
      setImportText('')
    },
    onError: (err: Error) => message.error(err.message),
  })

  const handleExport = async () => {
    try {
      const blob = await exportGoldenDataset()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'golden-dataset.json'
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      message.error((err as Error).message)
    }
  }

  const columns = [
    {
      title: 'Suite PR',
      key: 'suitePr',
      render: (_: unknown, r: GoldenDatasetEntry) =>
        `#${r.suitePr.originalPrNumber} ${r.suitePr.title ?? ''}`,
      ellipsis: true,
    },
    {
      title: 'File',
      key: 'file',
      render: (_: unknown, r: GoldenDatasetEntry) =>
        r.filePath ? (
          <Text code style={{ fontSize: 12 }}>
            {r.filePath}
            {r.lineNumber ? `:${r.lineNumber}` : ''}
          </Text>
        ) : (
          '—'
        ),
    },
    {
      title: 'Issue Type',
      dataIndex: 'issueType',
      key: 'issueType',
      render: (v: string | null) => (v ? <Tag>{v}</Tag> : '—'),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      render: (v: boolean, r: GoldenDatasetEntry) => (
        <Switch
          checked={v}
          size="small"
          onChange={(checked) => updateMutation.mutate({ id: r.id, data: { active: checked } })}
        />
      ),
      width: 80,
    },
    {
      title: 'Default',
      dataIndex: 'includedByDefault',
      key: 'includedByDefault',
      render: (v: boolean, r: GoldenDatasetEntry) => (
        <Switch
          checked={v}
          size="small"
          onChange={(checked) =>
            updateMutation.mutate({ id: r.id, data: { includedByDefault: checked } })
          }
        />
      ),
      width: 80,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => dayjs(v).format('MMM D, YYYY'),
      width: 130,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, r: GoldenDatasetEntry) => (
        <Popconfirm
          title="Delete this golden dataset entry?"
          onConfirm={() => deleteMutation.mutate(r.id)}
          okText="Delete"
          okType="danger"
        >
          <Button icon={<DeleteOutlined />} size="small" danger />
        </Popconfirm>
      ),
      width: 80,
    },
  ]

  if (isLoading) return <Spin size="large" />
  if (error) return <Alert type="error" message={(error as Error).message} />

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Title level={2} style={{ margin: 0 }}>
            Golden Dataset
          </Title>
          <Space>
            <span>Active only:</span>
            <Switch checked={activeOnly} onChange={setActiveOnly} />
          </Space>
        </div>
        <Space>
          <Button icon={<UploadOutlined />} onClick={() => setImportModalOpen(true)}>
            Import
          </Button>
          <Button icon={<DownloadOutlined />} onClick={handleExport}>
            Export
          </Button>
        </Space>
      </div>

      <Table
        dataSource={entries}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 20 }}
        scroll={{ x: true }}
      />

      <Modal
        title="Import Golden Dataset"
        open={importModalOpen}
        onCancel={() => {
          setImportModalOpen(false)
          setImportText('')
        }}
        onOk={() => importMutation.mutate(importText)}
        confirmLoading={importMutation.isPending}
        okText="Import"
        width={600}
      >
        <div style={{ marginBottom: 12 }}>
          <Text type="secondary">Paste JSON content or upload a file:</Text>
        </div>
        <Upload.Dragger
          accept=".json"
          showUploadList={false}
          beforeUpload={(file) => {
            const reader = new FileReader()
            reader.onload = (e) => {
              setImportText(e.target?.result as string)
            }
            reader.readAsText(file)
            return false
          }}
          style={{ marginBottom: 12 }}
        >
          <p>Click or drag JSON file here to upload</p>
        </Upload.Dragger>
        <Input.TextArea
          rows={10}
          value={importText}
          onChange={(e) => setImportText(e.target.value)}
          placeholder="Or paste JSON here..."
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
      </Modal>
    </div>
  )
}
