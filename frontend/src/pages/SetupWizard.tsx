import { useState } from 'react'
import {
  Typography,
  Card,
  Steps,
  Tag,
  Alert,
  List,
  Button,
  Spin,
  Row,
  Col,
  Space,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { getSetupStatus, getSetupInstructions, getSetupInstallations } from '../api/endpoints'

const { Title, Text } = Typography

function StatusIndicator({ ok, label }: { ok: boolean; label: string }) {
  return (
    <Space>
      {ok ? (
        <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 18 }} />
      ) : (
        <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
      )}
      <Text>{label}</Text>
      <Tag color={ok ? 'success' : 'error'}>{ok ? 'OK' : 'Failed'}</Tag>
    </Space>
  )
}

export default function SetupWizard() {
  const [currentStep, setCurrentStep] = useState(0)

  const { data: status, isLoading: statusLoading } = useQuery({
    queryKey: ['setup-status'],
    queryFn: getSetupStatus,
  })

  const { data: instructions, isLoading: instrLoading } = useQuery({
    queryKey: ['setup-instructions'],
    queryFn: getSetupInstructions,
    enabled: currentStep >= 1,
  })

  const { data: installations, isLoading: installLoading } = useQuery({
    queryKey: ['setup-installations'],
    queryFn: getSetupInstallations,
    enabled: currentStep >= 2,
  })

  const stepItems = [
    { title: 'Connection Status' },
    { title: 'Setup Instructions' },
    { title: 'Installations' },
  ]

  const step0 = (
    <Card title="GitHub App Connection Status">
      {statusLoading ? (
        <Spin />
      ) : !status ? (
        <Alert type="error" message="Failed to load status" />
      ) : (
        <>
          {status.errorMessage && (
            <Alert
              type="error"
              message="Connection Error"
              description={status.errorMessage}
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}
          <Row gutter={[16, 16]}>
            <Col span={12}>
              <Card size="small" title="App ID">
                <Text code>{status.appId || '(not configured)'}</Text>
              </Card>
            </Col>
            <Col span={12}>
              <Card size="small" title="Installation ID">
                <Text code>{status.installationId || '(not configured)'}</Text>
              </Card>
            </Col>
            <Col span={12}>
              <Card size="small" title="Default Mirror Org">
                <Text code>{status.defaultMirrorOrg || '(not configured)'}</Text>
              </Card>
            </Col>
            <Col span={24}>
              <Card size="small" title="Auth Status">
                <Space direction="vertical" size="small">
                  <StatusIndicator ok={status.jwtOk} label="JWT (GitHub App private key)" />
                  <StatusIndicator ok={status.tokenOk} label="Installation Token" />
                </Space>
              </Card>
            </Col>
          </Row>
        </>
      )}
    </Card>
  )

  const step1 = (
    <Card title="Setup Instructions">
      {instrLoading ? (
        <Spin />
      ) : !instructions ? (
        <Alert type="error" message="Failed to load instructions" />
      ) : (
        <Row gutter={16}>
          <Col span={8}>
            <Card size="small" title="Required Permissions">
              <List
                size="small"
                dataSource={instructions.requiredPermissions}
                renderItem={(item) => (
                  <List.Item>
                    <Tag color="blue">{item}</Tag>
                  </List.Item>
                )}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small" title="Required Events">
              <List
                size="small"
                dataSource={instructions.requiredEvents}
                renderItem={(item) => (
                  <List.Item>
                    <Tag color="purple">{item}</Tag>
                  </List.Item>
                )}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small" title="Setup Steps">
              <List
                size="small"
                dataSource={instructions.setupSteps}
                renderItem={(item, index) => (
                  <List.Item>
                    <Space>
                      <Tag>{index + 1}</Tag>
                      <Text>{item}</Text>
                    </Space>
                  </List.Item>
                )}
              />
            </Card>
          </Col>
        </Row>
      )}
    </Card>
  )

  const step2 = (
    <Card title="GitHub App Installations">
      {installLoading ? (
        <Spin />
      ) : !installations ? (
        <Alert type="error" message="Failed to load installations" />
      ) : (
        <pre
          style={{
            background: '#f5f5f5',
            padding: 16,
            borderRadius: 4,
            overflow: 'auto',
            maxHeight: 400,
            fontSize: 12,
          }}
        >
          {JSON.stringify(installations, null, 2)}
        </pre>
      )}
    </Card>
  )

  const steps = [step0, step1, step2]

  return (
    <div>
      <Title level={2}>Setup Wizard</Title>

      <Steps
        current={currentStep}
        items={stepItems}
        style={{ marginBottom: 24 }}
        onChange={setCurrentStep}
      />

      {steps[currentStep]}

      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Button disabled={currentStep === 0} onClick={() => setCurrentStep((s) => s - 1)}>
          Previous
        </Button>
        <Button
          type="primary"
          disabled={currentStep === steps.length - 1}
          onClick={() => setCurrentStep((s) => s + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  )
}
