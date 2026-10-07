#!/bin/bash
set -euo pipefail

# AWS ECR Configuration
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-905418111634}"
AWS_REGION="${AWS_REGION:-eu-west-1}"
ECR_REPO_PREFIX="springboot-kafka-microservices"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== Pushing Microservices to AWS ECR ===${NC}"
echo "Account: $AWS_ACCOUNT_ID"
echo "Region: $AWS_REGION"
echo ""

ECR_BASE_URL="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

# Maps the ECR repository name to the build context directory.
JAVA_SERVICES=("order-service:Order-service" "inventory-service:Inventory-service" "payment-service:Payment-service")
STATIC_SERVICES=("frontend:frontend")

ALL_REPOS=("order-service" "inventory-service" "payment-service" "frontend")

# Step 1: Create ECR repositories
echo -e "${GREEN}Step 1: Creating ECR repositories...${NC}"
for SERVICE in "${ALL_REPOS[@]}"; do
    REPO_NAME="$ECR_REPO_PREFIX/$SERVICE"
    echo "Ensuring: $REPO_NAME"
    aws ecr describe-repositories --repository-names "$REPO_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || \
    aws ecr create-repository \
        --repository-name "$REPO_NAME" \
        --region "$AWS_REGION" \
        --image-scanning-configuration scanOnPush=true \
        --image-tag-mutability MUTABLE >/dev/null
done
echo ""

# Step 2: Authenticate Docker with ECR
echo -e "${GREEN}Step 2: Authenticating Docker with ECR...${NC}"
if ! aws ecr get-login-password --region "$AWS_REGION" | \
    docker login --username AWS --password-stdin "$ECR_BASE_URL"; then
    echo -e "${RED}Error: Docker login failed. Check AWS credentials.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker authenticated${NC}"
echo ""

push_image() {
    local service="$1"
    local context="$2"
    local ecr_image="$ECR_BASE_URL/$ECR_REPO_PREFIX/$service:latest"

    echo "  Building image for $service..."
    docker build -t "$ecr_image" "$context"

    echo "  Pushing to ECR..."
    docker push "$ecr_image"
    echo -e "${GREEN}  ✓ Pushed $service${NC}"
}

# Step 3: Build the JARs the service images copy in
echo -e "${GREEN}Step 3: Building service JARs...${NC}"
for ENTRY in "${JAVA_SERVICES[@]}"; do
    CONTEXT="${ENTRY#*:}"
    echo "Building $CONTEXT..."
    (cd "$CONTEXT" && mvn -B -q clean package)
done
echo ""

# Step 4: Build and push images
echo -e "${GREEN}Step 4: Building and pushing images...${NC}"
for ENTRY in "${JAVA_SERVICES[@]}" "${STATIC_SERVICES[@]}"; do
    SERVICE="${ENTRY%%:*}"
    CONTEXT="${ENTRY#*:}"
    echo "Processing $SERVICE..."
    push_image "$SERVICE" "$CONTEXT"
    echo ""
done

echo -e "${GREEN}=== Complete ===${NC}"
echo ""
echo "Images available at:"
for SERVICE in "${ALL_REPOS[@]}"; do
    echo "  $ECR_BASE_URL/$ECR_REPO_PREFIX/$SERVICE:latest"
done
