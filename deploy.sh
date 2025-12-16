#!/bin/bash

# ===========================================
# Deployment Script for Garbage Collector App
# ===========================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Garbage Collector Deployment Script  ${NC}"
echo -e "${GREEN}========================================${NC}"

# Configuration
BACKEND_DIR="/home/omarsafi/IdeaProjects/GarbageCollectorBackend"
FRONTEND_DIR="/home/omarsafi/garbage-collector-frontend"
DEPLOY_DIR="/var/www/garbage-collector"
NGINX_CONF="/etc/nginx/sites-available/garbage-collector.conf"
SERVICE_NAME="garbage-collector-backend"

# Step 1: Build Frontend
echo -e "\n${YELLOW}[1/6] Building Angular Frontend...${NC}"
cd "$FRONTEND_DIR"
npm install
npm run build -- --configuration=production
echo -e "${GREEN}✓ Frontend built successfully${NC}"

# Step 2: Deploy Frontend to Nginx
echo -e "\n${YELLOW}[2/6] Deploying Frontend to Nginx...${NC}"
sudo mkdir -p "$DEPLOY_DIR"
sudo rm -rf "$DEPLOY_DIR/browser"
sudo cp -r dist/garbage-collector-frontend/browser "$DEPLOY_DIR/"
sudo chown -R www-data:www-data "$DEPLOY_DIR"
echo -e "${GREEN}✓ Frontend deployed to $DEPLOY_DIR${NC}"

# Step 3: Build Backend
echo -e "\n${YELLOW}[3/6] Building Spring Boot Backend...${NC}"
cd "$BACKEND_DIR"
./mvnw clean package -DskipTests
echo -e "${GREEN}✓ Backend built successfully${NC}"

# Step 4: Deploy Backend JAR
echo -e "\n${YELLOW}[4/6] Deploying Backend JAR...${NC}"
sudo mkdir -p /opt/garbage-collector
sudo cp target/*.jar /opt/garbage-collector/garbage-collector.jar
echo -e "${GREEN}✓ Backend JAR deployed${NC}"

# Step 5: Configure Nginx
echo -e "\n${YELLOW}[5/6] Configuring Nginx...${NC}"
sudo cp "$BACKEND_DIR/nginx/garbage-collector.conf" "$NGINX_CONF"
sudo ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
echo -e "${GREEN}✓ Nginx configured and reloaded${NC}"

# Step 6: Restart Backend Service
echo -e "\n${YELLOW}[6/6] Restarting Backend Service...${NC}"
if systemctl is-active --quiet "$SERVICE_NAME"; then
    sudo systemctl restart "$SERVICE_NAME"
else
    echo -e "${YELLOW}Service not found. Creating systemd service...${NC}"
    sudo tee /etc/systemd/system/$SERVICE_NAME.service > /dev/null << EOF
[Unit]
Description=Garbage Collector Backend
After=network.target mongodb.service redis.service

[Service]
Type=simple
User=www-data
ExecStart=/usr/bin/java -jar /opt/garbage-collector/garbage-collector.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=garbage-collector
Environment="JAVA_OPTS=-Xms512m -Xmx1024m"

[Install]
WantedBy=multi-user.target
EOF
    sudo systemctl daemon-reload
    sudo systemctl enable "$SERVICE_NAME"
    sudo systemctl start "$SERVICE_NAME"
fi
echo -e "${GREEN}✓ Backend service started${NC}"

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Deployment Complete!                  ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Frontend: http://localhost"
echo -e "Backend:  http://localhost/api"
echo -e "WebSocket: ws://localhost/ws"
echo -e "\nCheck backend logs: sudo journalctl -u $SERVICE_NAME -f"
