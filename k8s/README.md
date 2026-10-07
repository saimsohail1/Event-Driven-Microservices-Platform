# Kubernetes / EKS Deployment

## Prerequisites

1. **EKS cluster** — create with `eksctl create cluster -f eks-cluster-config.yaml`
2. **kubectl configured** — `aws eks update-kubeconfig --name microservices-cluster --region eu-west-1`
3. **ECR access** — attach `AmazonEC2ContainerRegistryReadOnly` to the node group IAM role
4. **Images pushed** — run `../push-to-ecr.sh`, or let the GitHub Actions workflows do it
5. **Databases** — three RDS PostgreSQL instances matching the `DB_URL` values in each `configmap.yaml`

Everything below assumes the `default` namespace, which is what the manifests
and the CI workflows use.

## 1. Create the database secrets

The secrets are deliberately **not** in git. Create them directly, substituting
your real passwords:

```bash
kubectl create secret generic order-service-secret     --from-literal=DB_PASSWORD='<order-db-password>'
kubectl create secret generic inventory-service-secret --from-literal=DB_PASSWORD='<inventory-db-password>'
kubectl create secret generic payment-service-secret   --from-literal=DB_PASSWORD='<payment-db-password>'
```

Alternatively copy each `secret.example.yaml` to `secret.yaml` (git-ignored),
fill in the password and apply it.

## 2. Deploy Kafka

The services cannot do anything without a broker, so this goes first:

```bash
kubectl apply -f kafka/zookeeper.yaml
kubectl apply -f kafka/kafka.yaml
kubectl rollout status statefulset/kafka --timeout=300s
```

Kafka is advertised in-cluster as `kafka:9092`, which is the value every
service's ConfigMap uses. Order Service creates the `order.created` topic and
its `.DLT` counterpart on startup.

> Both StatefulSets use `emptyDir` so they deploy on a cluster with no EBS CSI
> driver. Topic data does not survive a pod restart. Switch to
> PersistentVolumeClaims before using this for anything real.

## 3. Deploy the services

```bash
for svc in order-service inventory-service payment-service; do
  kubectl apply -f $svc/configmap.yaml
  kubectl apply -f $svc/service.yaml
  kubectl apply -f $svc/deployment.yaml
done

kubectl apply -f frontend/service.yaml
kubectl apply -f frontend/deployment.yaml
```

## 4. Verify

```bash
kubectl get pods
kubectl rollout status deployment/order-service --timeout=300s
kubectl logs -f deployment/order-service
```

## 5. Access the dashboard

The frontend is the only public entry point; it proxies `/api/*` to the three
ClusterIP services.

```bash
kubectl get svc frontend -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

Open that hostname in a browser.

## Notes

- **Health checks** use `/actuator/health/liveness` and `/actuator/health/readiness`. Liveness excludes the database on purpose, so a brief RDS blip does not restart healthy pods.
- **Capacity**: the node group is two `t3.medium` nodes. CPU requests for Kafka, ZooKeeper, the frontend and two replicas of each service do not fit on one 2-vCPU node.
- **Schema**: Flyway runs at service startup, so the RDS user needs DDL permission on its own database.
- **Logging**: install Loki/Promtail with `helm upgrade --install loki grafana/loki-stack -n monitoring --create-namespace -f loki/loki-values.yaml`.
