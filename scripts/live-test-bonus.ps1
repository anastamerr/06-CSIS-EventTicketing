param(
    [string]$Namespace = "eventticketing",
    [string]$IngressHost = "eventticketing.local",
    [string]$GatewayHealthPath = "/actuator/health",
    [string]$BookingLoadPath = "/api/bookings/1"
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required for live bonus verification."
    }
}

Require-Command kubectl
Require-Command minikube

Write-Host "Enabling Minikube ingress and metrics-server addons..."
minikube addons enable ingress | Out-Host
minikube addons enable metrics-server | Out-Host

Write-Host "Applying Ingress and booking-service HPA manifests..."
kubectl apply -f k8s/api-gateway/gateway-ingress.yaml | Out-Host
kubectl apply -f k8s/hpa/booking-service-hpa.yaml | Out-Host

Write-Host "Waiting for ingress controller..."
kubectl wait --namespace ingress-nginx `
    --for=condition=ready pod `
    --selector=app.kubernetes.io/component=controller `
    --timeout=180s | Out-Host

$minikubeIp = (minikube ip).Trim()
Write-Host "Minikube IP: $minikubeIp"
Write-Host "Add this hosts entry if it is not already present: $minikubeIp $IngressHost"

Write-Host "Checking HPA object..."
kubectl get hpa booking-service -n $Namespace | Out-Host

Write-Host "Checking gateway through Ingress..."
$headers = @{ Host = $IngressHost }
$healthUrl = "http://$minikubeIp$GatewayHealthPath"
Invoke-WebRequest -Uri $healthUrl -Headers $headers -UseBasicParsing -TimeoutSec 15 | Select-Object StatusCode, Content | Format-List | Out-Host

Write-Host "Optional load probe for HPA visibility. This does not authenticate application endpoints."
Write-Host "Run a proper authenticated load against http://$IngressHost$BookingLoadPath, then watch:"
Write-Host "kubectl get hpa booking-service -n $Namespace --watch"
