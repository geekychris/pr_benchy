import { Typography, Card, Button, Descriptions, Tag, Alert } from 'antd'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getSetupStatus } from '../api/endpoints'
import { CheckCircleOutlined, CloseCircleOutlined, SettingOutlined } from '@ant-design/icons'

const { Title, Text } = Typography

export default function Settings() {
  const { data: status, isLoading, error } = useQuery({
    queryKey: ['setup-status'],
    queryFn: getSetupStatus,
  })

  return (
    <div>
      <Title level={2}>Settings</Title>

      <Card
        title="GitHub App Connection"
        style={{ marginBottom: 16 }}
        extra={
          <Link to="/setup">
            <Button icon={<SettingOutlined />}>Setup Wizard</Button>
          </Link>
        }
      >
        {isLoading && <Text>Loading...</Text>}
        {error && (
          <Alert type="error" message="Could not load setup status" style={{ marginBottom: 12 }} />
        )}
        {status && (
          <>
            {status.errorMessage && (
              <Alert
                type="error"
                message={status.errorMessage}
                style={{ marginBottom: 12 }}
              />
            )}
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="App ID">{status.appId || '—'}</Descriptions.Item>
              <Descriptions.Item label="Installation ID">
                {status.installationId || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Default Mirror Org">
                {status.defaultMirrorOrg || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="JWT Status">
                {status.jwtOk ? (
                  <Tag icon={<CheckCircleOutlined />} color="success">
                    OK
                  </Tag>
                ) : (
                  <Tag icon={<CloseCircleOutlined />} color="error">
                    Failed
                  </Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Token Status">
                {status.tokenOk ? (
                  <Tag icon={<CheckCircleOutlined />} color="success">
                    OK
                  </Tag>
                ) : (
                  <Tag icon={<CloseCircleOutlined />} color="error">
                    Failed
                  </Tag>
                )}
              </Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Card>

      <Card title="About">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Application">PR Bot Benchmark</Descriptions.Item>
          <Descriptions.Item label="Purpose">
            Benchmark and evaluate AI PR review bots against exemplar repositories
          </Descriptions.Item>
          <Descriptions.Item label="API Backend">
            <Text code>http://localhost:8080</Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  )
}
