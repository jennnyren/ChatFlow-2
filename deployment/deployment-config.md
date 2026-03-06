# Deployment Configuration

## EC2 Setup

**chatflow Server 1**
- Instance ID: `i-04c4b9050ecccf4fe`
- Instance Type: t3 micro

**chatflow Server 2**
- Instance ID: `i-0e411c13e43a6825a`
- Instance Type: t3 micro

**chatflow Server 3**
- Instance ID: `i-062dabd10cb071557`
- Instance Type: t3 micro

**chatflow Server 4**
- Instance ID: `i-0287e3ecec0b3730e`
- Instance Type: t3 micro

**Consumer**
- Instance ID: `i-00e181caded8ab058`
- Instance Type: t3 micro

**RabbitMQ**
- Instance ID: `i-04468b016d03e5e26`
- Instance Type: t3 micro

**Security Group Inbound Rules**
- Port 22 (TCP) — SSH — Source: My IP
- Port 8080 (TCP) — WebSocket server — Source: ALB SG
- Port 5672 (TCP) — RabbitMQ AMQP — Source: chatflow SG
- Port 15672 (TCP) — RabbitMQ Management UI — Source: My IP

---

## ALB Configuration

- ALB Name: chatflow-server-lb
- Scheme: internet-facing
- Listener Port: 80
- Target Group Name: chatflow-server-tg
- Target Group Protocol: HTTP
- Health Check Path: `/health`
- Health Check Interval: 30s
- Health Check Timeout: 5s
- Healthy Threshold: 2
- Unhealthy Threshold: 3
- Idle Timeout: s (must be > 60s)
- Stickiness: Enabled
- Stickiness Cookie Duration: s

**Target Group Members**
- Server 1 — Port 8080 — Status: healthy
- Server 2 — Port 8080 — Status: healthy
- ...

---

## RabbitMQ Setup

- Exchange Name: `chat.exchange`
- Exchange Type: topic
- Routing Key Pattern: `room.{roomId}`
- Queue Names: `room.1` through `room.20`
- Message TTL: 60s
- Max Queue Length: 1000 messages
- Management UI Port: 15672

**Queue Bindings**

Each queue `room.N` is bound to `chat.exchange` with routing key `room.N`.