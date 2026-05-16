# ALB Configuration

## Load Balancer

| Parameter | Value |
|---|---|
| Name | chat-servers |
| Type | Application Load Balancer |
| Scheme | Internet-facing |
| DNS Name | chat-servers-1288018322.us-west-2.elb.amazonaws.com |
| Region | us-west-2 |
| Availability Zones | us-west-2a, us-west-2b, us-west-2c, us-west-2d |
| IP Address Type | IPv4 |
| Connection Idle Timeout | 120 seconds |
| Cross-zone Load Balancing | On |
| HTTP/2 | On |

## Listener

| Parameter | Value |
|---|---|
| Protocol | HTTP |
| Port | 80 |
| Default Action | Forward to target group `chat-servers` (100%) |
| Target Group Stickiness | Enabled (86,400 seconds / 1 day) |

## Target Group

| Parameter | Value |
|---|---|
| Name | chat-servers |
| Target Type | Instance |
| Protocol | HTTP |
| Port | 8080 |
| Protocol Version | HTTP1 |
| Load Balancing Algorithm | Round Robin |
| Total Targets | 4 |

## Health Check

| Parameter | Value |
|---|---|
| Protocol | HTTP |
| Path | /health |
| Port | 8080 |
| Healthy Threshold | 5 consecutive successes |
| Unhealthy Threshold | 2 consecutive failures |
| Timeout | 5 seconds |
| Interval | 30 seconds |
| Success Codes | 200 |

## Sticky Session

| Parameter | Value |
|---|---|
| Stickiness | Enabled |
| Type | Load balancer generated cookie (lb_cookie) |
| Duration | 86,400 seconds (1 day) |